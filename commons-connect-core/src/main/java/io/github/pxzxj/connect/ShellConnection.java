package io.github.pxzxj.connect;

import com.pty4j.PtyProcess;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * inputStream.available()方法失效，因此重新实现读取回显逻辑，参考https://github.com/JetBrains/pty4j/pull/37
 */
public class ShellConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnection.class);

    private final String connectionId;
    private final long createTime;
    private volatile long lastSendTime;
    private final ConnectionConfigurer connectionConfigurer;
    private final PtyProcess ptyProcess;
    private final long pid;
    private final Reader reader;
    private final Writer writer;

    private int offset = 0;
    private final StringBuffer outputBuffer = new StringBuffer();
    private String postConnectOutput = "";
    private CommandConfigurer currentCommandConfigurer;
    private CommandResult currentCommandResult;

    private Thread outputReaderThread;
    private Thread keepAliveThread;

    public ShellConnection(String connectionId, ConnectionConfigurer connectionConfigurer, PtyProcess ptyProcess) throws UnsupportedEncodingException {
        this.connectionId = connectionId;
        this.createTime = System.currentTimeMillis();
        this.lastSendTime = System.currentTimeMillis();
        this.connectionConfigurer = connectionConfigurer;
        this.ptyProcess = ptyProcess;
        this.pid = ptyProcess.pid();
        this.reader = new InputStreamReader(ptyProcess.getInputStream(), connectionConfigurer.getCharset());
        this.writer = new OutputStreamWriter(ptyProcess.getOutputStream(), connectionConfigurer.getCharset());
        this.currentCommandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("")
                                                            .successFlags(connectionConfigurer.getSuccessFlags())
                                                            .failFlags(connectionConfigurer.getFailFlags())
                                                            .timeoutMilliSeconds(connectionConfigurer.getTimeoutMilliSeconds())
                                                            .build();
        this.currentCommandResult = CommandResult.successfulResult();
        logger.info("create process success, pid: {}", this.pid);
    }

    public PtyProcess getPtyProcess() {
        return ptyProcess;
    }

    @Override
    public String getPostConnectOutput() {
        return postConnectOutput;
    }

    public void postConnect() {
        outputReaderThread = new Thread(new ShellOutputReader());
        outputReaderThread.setName("ShellOutputReader " + pid);
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
        waitForString();
        if(!currentCommandResult.isSuccess()) {
            if(isConnected()){
                close();
            }
            throw new GeneralConnectionException("连接失败, 回显内容:" + outputBuffer);
        }
        postConnectOutput = outputBuffer.toString();
        CommandConfigurer postConnectCommand = connectionConfigurer.getPostConnect();
        if (postConnectCommand != null) {
            if (postConnectCommand.getCommand() == null && postConnectCommand.getCommandFunction() != null) {
                postConnectCommand.setCommand(postConnectCommand.getCommandFunction().apply(postConnectOutput));
            }
            CommandResult commandResult = sendCommand(postConnectCommand);
            if (!commandResult.isSuccess()) {
                if(isConnected()){
                    close();
                }
                String message = "连接后执行命令失败：" + commandResult.getFailCommand() + "\n回显内容：" + commandResult.getResult();
                throw new GeneralCommandException(message, commandResult);
            }
            postConnectOutput += commandResult.getResult();
        }
		logger.info("id: {}, postConnectOutput: {}", connectionId, postConnectOutput);
        if(StringUtils.isNotEmpty(connectionConfigurer.getKeepAliveCommand())){
            keepAliveThread = new Thread(new KeepAliveDaemon());
            keepAliveThread.setName("KeepAliveDaemon " + pid);
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();
        }
    }

    public synchronized void sendString(String command, String enter) {
        try {
            command += enter;
			logger.info("id: {}, send command: {}", connectionId, command);
            lastSendTime = System.currentTimeMillis();
            writer.write(command);
            writer.flush();
        } catch (IOException e) {
			logger.error("id: {}, command send fail: {}", connectionId, command, e);
            throw new GeneralCommandException("command send fail: " + command, e);
        }
    }

    @Override
    public synchronized CommandResult sendCommand(CommandConfigurer commandConfigurer) {
        CommandConfigurer cc = commandConfigurer;
        Objects.requireNonNull(cc.getCommand(), "first command cannot be null");
        CommandResult commandResult = new CommandResult(commandConfigurer);
        outputBuffer.setLength(0);
        while (cc != null) {
            offset = outputBuffer.length();
            if (cc.getCommand() == null && cc.getCommandFunction() == null) {
                throw new IllegalArgumentException("command and commandFunction cannot be null in same time");
            }
            this.currentCommandConfigurer = cc;
            this.currentCommandResult = CommandResult.successfulResult();
            String enter = cc.getEnter();
            String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(outputBuffer.substring(offset));
            sendString(command, enter);
            waitForString();
            if(!currentCommandResult.isSuccess() ||
                    (currentCommandResult.getResult() == null && currentCommandConfigurer.getSuccessFlags() != null)) {
                commandResult.setSuccess(false);
                commandResult.setFailCommand(cc);
                break;
            }
            cc = cc.getNext();
        }
        commandResult.setResult(outputBuffer.substring(offset));
        return commandResult;
    }

    public synchronized void waitForString() {
        long startTime = lastSendTime = System.currentTimeMillis();
        int timeoutMilliSeconds = currentCommandConfigurer.getTimeoutMilliSeconds();
        String moreCommand = currentCommandConfigurer.getMoreCommand();
        String moreFlag = currentCommandConfigurer.getMoreFlag();
        try {
            while (System.currentTimeMillis() - startTime < timeoutMilliSeconds && currentCommandResult.getResult() == null) {
                if (moreFlag != null) {
                    sendString(moreCommand, "");
                } else if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval()) {
                    lastSendTime = System.currentTimeMillis();
                    sendString(connectionConfigurer.getKeepAliveCommand(), "");
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
        } catch (InterruptedException e) {
            logger.error("", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connectionConfigurer.getPreDisconnect() != null) {
                CommandResult commandResult = this.sendCommand(connectionConfigurer.getPreDisconnect());
                logger.info("preLogout result: {}", commandResult.getResult());
            }
            if(outputReaderThread != null) {
                outputReaderThread.interrupt();
            }
            if(keepAliveThread != null) {
                keepAliveThread.interrupt();
            }

        } catch (Exception e){
            logger.error("logout error ", e);
        }
        try {
            logger.info("process destroy, pid: {}", pid);
            ptyProcess.destroy();
        } catch (Exception e){
            logger.error("close connection error ", e);
        }
    }

    @Override
    public boolean isConnected() {
        return ptyProcess.isAlive();
    }

    @Override
    public String getConnectionId() {
        return this.connectionId;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public ConnectionConfigurer getConnectionConfigurer() {
        return connectionConfigurer;
    }

    private class ShellOutputReader implements Runnable {

        @Override
        public void run() {
			logger.info("{} start", Thread.currentThread().getName());
            char[] buffer = new char[1024];
            try {
                while (!Thread.interrupted()) {
                    int len = reader.read(buffer);
                    if(len == -1) {
                        break;
                    }
                    int fromIndex = outputBuffer.length();
                    outputBuffer.append(buffer, 0, len);
                    boolean failed = matchFlags(currentCommandConfigurer.getFailFlags(), fromIndex);
                    boolean success = false;
                    if(!failed) {
                        success = matchFlags(currentCommandConfigurer.getSuccessFlags(), fromIndex);
                    }
                    if(success | failed) {
                        if(currentCommandConfigurer.getSuccessFlags() != null || currentCommandConfigurer.getFailFlags() != null) {
                            currentCommandResult.setSuccess(success);
                        }
                        currentCommandResult.setResult(outputBuffer.substring(fromIndex));
                    }
                }
            } catch (IOException e) {
                logger.error("ShellOutputReader read exception ", e);
            }
			logger.info("{} Terminated", Thread.currentThread().getName());
        }

        private boolean matchFlags(String[] flags, int fromIndex) {
            if(flags == null) {
                return false;
            }
            for(String flag : flags) {
                int fi = fromIndex;
                if(fi - flag.length() > offset) {
                    fi = fi - flag.length();
                }
                if(outputBuffer.indexOf(flag, fi) != -1) {
                    return true;
                }
            }
            return false;
        }
    }

    private class KeepAliveDaemon implements Runnable {

        private long lastSendKeepAliveCommandTime = System.currentTimeMillis();

        @Override
        public void run() {
			logger.info("{} start", Thread.currentThread().getName());
            CommandConfigurer keepAliveCommand = CommandConfigurerBuilder.newCommandConfigurer(connectionConfigurer.getKeepAliveCommand())
                    .enter("")
                    .successFlags(connectionConfigurer.getKeepAliveWaitStr())
                    .timeoutMilliSeconds(connectionConfigurer.getKeepAliveWaitTimeout())
                    .build();
            try {

                while (!Thread.interrupted()) {
                    if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval() &&
                            System.currentTimeMillis() - lastSendKeepAliveCommandTime > connectionConfigurer.getKeepAliveInterval()) {
                        synchronized (ShellConnection.this){
                            if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval() &&
                                    System.currentTimeMillis() - lastSendKeepAliveCommandTime > connectionConfigurer.getKeepAliveInterval()) {
                                long tempLastSendTime = lastSendTime;
                                lastSendKeepAliveCommandTime = System.currentTimeMillis();
                                CommandResult commandResult = sendCommand(keepAliveCommand);
								logger.info("pid: {}, keepAliveResult: {}", pid, commandResult);
                                lastSendTime = tempLastSendTime;
                            }
                        }
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                logger.error(Thread.currentThread().getName(), e);
                if (!isConnected()) {
					logger.error("{} Exit, Connection Closed", Thread.currentThread().getName());
                }
            }
			logger.info("{} Terminated", Thread.currentThread().getName());
        }
    }
}
