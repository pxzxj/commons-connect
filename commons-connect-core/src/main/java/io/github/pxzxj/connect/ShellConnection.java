package io.github.pxzxj.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ponshine.connection.event.ConnectionClosedEvent;
import com.pty4j.PtyProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * inputStream.available()方法失效，因此重新实现读取回显逻辑，参考https://github.com/JetBrains/pty4j/pull/37
 */
public class ShellConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnection.class);

    private final String connectionId;
    private final long createTime;
    private volatile long lastAccessTime;
    private final ConnectionConfigurer connectionConfigurer;
    private final PtyProcess ptyProcess;
    private final int pid;
    private final Reader reader;
    private final Writer writer;

    private int offset = 0;
    private StringBuffer fullScreen = new StringBuffer();
    private final ObjectMapper om = new ObjectMapper();

    private CommandConfigurer currentCommandConfigurer;
    private CommandResult currentCommandResult;

    private Thread outputReaderThread;
    private Thread keepActiveThread;

    private ApplicationEventPublisher applicationEventPublisher;

    public ShellConnection(String connectionId, ConnectionConfigurer connectionConfigurer, PtyProcess ptyProcess) throws UnsupportedEncodingException {
        this.connectionId = connectionId;
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
        this.connectionConfigurer = connectionConfigurer;
        this.ptyProcess = ptyProcess;
        this.pid = ptyProcess.getPid();
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

    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
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
            throw new GeneralConnectionException("连接失败, 回显内容:" + getFullScreen());
        }
        CommandConfigurer postConnectCommand = connectionConfigurer.getPostConnect();
        if (postConnectCommand != null) {
            if (postConnectCommand.getCommand() == null && postConnectCommand.getCommandFunction() != null) {
                postConnectCommand.setCommand(postConnectCommand.getCommandFunction().apply(getLastScreen()));
            }
            CommandResult commandResult = sendCommand(postConnectCommand);
            if (!commandResult.isSuccess()) {
                if(isConnected()){
                    close();
                }
                String message = "连接后执行命令失败：" + commandResult.getFailCommand() + "\n回显内容：" + commandResult.getResult();
                throw new GeneralExecException(message, commandResult);
            }
        }
        logger.info("host: " + connectionConfigurer.getHost() + ", login output: " + getFullScreen());
        if(StringUtils.hasLength(connectionConfigurer.getKeepActiveCommand())){
            keepActiveThread = new Thread(new KeepActiveDaemon());
            keepActiveThread.setName("KeepActiveDaemon " + pid);
            keepActiveThread.setDaemon(true);
            keepActiveThread.start();
        }
    }

    @Override
    public synchronized void sendString(String command) {
        sendString(command, CommandConfigurer.BACKSLASH_N);
    }

    public synchronized void sendString(String command, String enter) {
        try {
            command += enter;
            logger.info("host: " + connectionConfigurer.getHost() + ", send command: " + om.writeValueAsString(command));
            lastAccessTime = System.currentTimeMillis();
            writer.write(command);
            writer.flush();
        } catch (IOException e) {
            logger.error("command send fail: " + command, e);
            throw new GeneralExecException("command send fail: " + command, e);
        }
    }

    @Override
    public CommandResult sendStringWithResult(String command) {
        throw new IllegalArgumentException("NOT SUPPORTED METHOD");
    }

    @Override
    public CommandResult sendStringWithResult(String command, String waitStr, int maxMilliSeconds) {
        throw new IllegalArgumentException("NOT SUPPORTED METHOD");
    }

    @Override
    public synchronized CommandResult sendCommand(CommandConfigurer commandConfigurer) {
        CommandConfigurer cc = commandConfigurer;
        if (cc.getCommand() == null) {
            throw new IllegalArgumentException("first command cannot be null");
        }
        CommandResult commandResult = new CommandResult(commandConfigurer);
        offset = fullScreen.length();
        while (cc != null) {
            if (cc.getCommand() == null && cc.getCommandFunction() == null) {
                throw new IllegalArgumentException("command and commandFunction cannot be null in same time");
            }
            this.currentCommandConfigurer = cc;
            this.currentCommandResult = CommandResult.successfulResult();
            String enter = cc.getEnter();
            String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(fullScreen.substring(offset));
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
        commandResult.setResult(fullScreen.substring(offset));
        return commandResult;
    }

    @Override
    public synchronized boolean waitForString(String string, int maxMilliSeconds) {
        waitForString();
        return false;
    }

    public synchronized void waitForString() {
        long startTime = lastAccessTime = System.currentTimeMillis();
        int timeoutMilliSeconds = currentCommandConfigurer.getTimeoutMilliSeconds();
        String moreCommand = currentCommandConfigurer.getMoreCommand();
        String moreFlag = currentCommandConfigurer.getMoreFlag();
        try {
            while (System.currentTimeMillis() - startTime < timeoutMilliSeconds && currentCommandResult.getResult() == null) {
                if (moreFlag != null) {
                    sendString(moreCommand, "");
                } else if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval()) {
                    lastAccessTime = System.currentTimeMillis();
                    sendString(connectionConfigurer.getKeepActiveCommand(), "");
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
        } catch (InterruptedException e) {
            logger.error("", e);
        }
    }

    @Override
    public synchronized void close() {
        if(applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ConnectionClosedEvent(this));
        }
        try {
            if (connectionConfigurer.getPreLogout() != null) {
                CommandResult commandResult = this.sendCommand(connectionConfigurer.getPreLogout());
                logger.info("preLogout result: {}", commandResult.getResult());
            }
            if(outputReaderThread != null) {
                outputReaderThread.interrupt();
            }
            if(keepActiveThread != null) {
                keepActiveThread.interrupt();
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
    public synchronized long getLastAccessTime() {
        return this.lastAccessTime;
    }

    @Override
    public ConnectionConfigurer getConnectionConfigurer() {
        return connectionConfigurer;
    }

    @Override
    public String getLastScreen() {
        return fullScreen.substring(offset);
    }

    @Override
    public void cleanScreen() {
        fullScreen.setLength(0);
    }

    @Override
    public String getFullScreen() {
        return fullScreen.toString();
    }

    private class ShellOutputReader implements Runnable {

        @Override
        public void run() {
            logger.info(Thread.currentThread().getName() + " start");
            char[] buffer = new char[1024];
            try {
                while (!Thread.interrupted()) {
                    int len = reader.read(buffer);
                    if(len == -1) {
                        break;
                    }
                    int fromIndex = fullScreen.length();
                    fullScreen.append(buffer, 0, len);
                    boolean failed = matchFlags(currentCommandConfigurer.getFailFlags(), fromIndex);
                    boolean success = false;
                    if(!failed) {
                        success = matchFlags(currentCommandConfigurer.getSuccessFlags(), fromIndex);
                    }
                    if(success | failed) {
                        if(currentCommandConfigurer.getSuccessFlags() != null || currentCommandConfigurer.getFailFlags() != null) {
                            currentCommandResult.setSuccess(success);
                        }
                        currentCommandResult.setResult(fullScreen.substring(fromIndex));
                    }
                }
            } catch (IOException e) {
                logger.error("ShellOutputReader read exception ", e);
            }
            logger.info(Thread.currentThread().getName() + " Terminated");
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
                if(fullScreen.indexOf(flag, fi) != -1) {
                    return true;
                }
            }
            return false;
        }
    }

    private class KeepActiveDaemon implements Runnable {

        private long lastSendKeepActiveCommandTime = System.currentTimeMillis();

        @Override
        public void run() {
            logger.info(Thread.currentThread().getName() + " start");
            CommandConfigurer keepActiveCommand = CommandConfigurerBuilder.newCommandConfigurer(connectionConfigurer.getKeepActiveCommand())
                    .enter("")
                    .successFlags(connectionConfigurer.getKeepActiveWaitStr())
                    .timeoutMilliSeconds(connectionConfigurer.getKeepActiveWaitTimeout())
                    .build();
            try {

                while (!Thread.interrupted()) {
                    if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval() &&
                            System.currentTimeMillis() - lastSendKeepActiveCommandTime > connectionConfigurer.getKeepActiveInterval()) {
                        synchronized (ShellConnection.this){
                            if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval() &&
                                    System.currentTimeMillis() - lastSendKeepActiveCommandTime > connectionConfigurer.getKeepActiveInterval()) {
                                long tempLastAccessTime = lastAccessTime;
                                lastSendKeepActiveCommandTime = System.currentTimeMillis();
                                CommandResult commandResult = sendCommand(keepActiveCommand);
                                logger.info("pid: " + pid + ", keepActiveResult: " + commandResult);
                                lastAccessTime = tempLastAccessTime;
                            }
                        }
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                logger.error(Thread.currentThread().getName(), e);
                if (!isConnected()) {
                    logger.error(Thread.currentThread().getName() + " Exit, Connection Closed");
                }
            }
            logger.info(Thread.currentThread().getName() + " Terminated");
        }
    }
}
