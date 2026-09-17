package io.github.pxzxj.connect;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

import com.pty4j.PtyProcess;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * inputStream.available() does not work for a pty, so the echo is read with a separate implementation, see https://github.com/JetBrains/pty4j/pull/37
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

    private volatile int offset = 0;
    private final StringBuffer outputBuffer = new StringBuffer();
    private String postConnectOutput = "";
    private volatile CommandConfigurer currentCommandConfigurer;
    private volatile CommandResult currentCommandResult;

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
        logger.info("id: {}, create process success, pid: {}", connectionId, this.pid);
    }

    public PtyProcess getPtyProcess() {
        return ptyProcess;
    }

    @Override
    public String getPostConnectOutput() {
        return postConnectOutput;
    }

    public void postConnect() {
		currentCommandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("")
				.successFlags(connectionConfigurer.getSuccessFlags())
				.failFlags(connectionConfigurer.getFailFlags())
				.timeoutMilliSeconds(connectionConfigurer.getTimeoutMilliSeconds())
				.build();
		currentCommandResult = ArrayUtils.isNotEmpty(connectionConfigurer.getSuccessFlags()) ? CommandResult.failedResult("") : CommandResult.successfulResult("");
        outputReaderThread = new Thread(new ShellOutputReader());
        outputReaderThread.setName("ShellOutputReader " + pid);
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
        waitForString();
        if(!currentCommandResult.isSuccess()) {
            if(isConnected()){
                close();
            }
            throw new GeneralConnectionException("connection failed, echo: " + outputBuffer);
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
                String message = "post connect command failed: " + commandResult.getFailCommand() + "\n echo: " + commandResult.getResult();
                throw new GeneralCommandException(message, commandResult);
            }
            postConnectOutput += commandResult.getResult();
        }
		logger.info("id: {}, postConnectOutput: {}", connectionId, postConnectOutput);
        if(StringUtils.isNotEmpty(connectionConfigurer.getKeepAliveCommand())){
            keepAliveThread = new Thread(new KeepAliveDaemon());
            keepAliveThread.setName("KeepAliveDaemon " + connectionId);
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();
        }
    }

    private void sendString(String command, String enter) {
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
		if (cc.getCommand() == null) {
			return CommandResult.failedResult("first command cannot be null");
		}
        CommandResult commandResult = new CommandResult(commandConfigurer);
		synchronized (outputBuffer) {
			outputBuffer.setLength(0);
			offset = 0;
		}
        while (cc != null) {
			String lastOutput = "";
			if(outputBuffer.length() > 0){
				lastOutput = outputBuffer.substring(offset);
			}
            offset = outputBuffer.length();
            if (cc.getCommand() == null && cc.getCommandFunction() == null) {
				return CommandResult.failedResult("command and commandFunction cannot be null in same time");
            }
            this.currentCommandConfigurer = cc;
            this.currentCommandResult = ArrayUtils.isNotEmpty(currentCommandConfigurer.getSuccessFlags()) ? CommandResult.failedResult("") : CommandResult.successfulResult("");
            String enter = cc.getEnter();
            String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(lastOutput);
            sendString(command, enter);
            waitForString();
            if(!currentCommandResult.isSuccess()) {
                commandResult.setSuccess(false);
                commandResult.setFailCommand(cc);
                break;
            }
            cc = cc.getNext();
        }
        commandResult.setResult(outputBuffer.toString());
        return commandResult;
    }

    public void waitForString() {
        long startTime = lastSendTime = System.currentTimeMillis();
        int timeoutMilliSeconds = currentCommandConfigurer.getTimeoutMilliSeconds();
        String moreCommand = currentCommandConfigurer.getMoreCommand();
        String moreFlag = currentCommandConfigurer.getMoreFlag();
        try {
            while (System.currentTimeMillis() - startTime < timeoutMilliSeconds && StringUtils.isEmpty(currentCommandResult.getResult())) {
                if (moreFlag != null) {
                    sendString(moreCommand, "");
                } else if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval()) {
                    lastSendTime = System.currentTimeMillis();
                    sendString(connectionConfigurer.getKeepAliveCommand(), "");
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
        } catch (InterruptedException e) {
            logger.error("id: {}", connectionId, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connectionConfigurer.getPreDisconnect() != null) {
                CommandResult commandResult = this.sendCommand(connectionConfigurer.getPreDisconnect());
                logger.info("id: {}, preDisconnect result: {}", connectionId, commandResult.getResult());
            }
            if(outputReaderThread != null) {
                outputReaderThread.interrupt();
            }
            if(keepAliveThread != null) {
                keepAliveThread.interrupt();
            }

        } catch (Exception e){
            logger.error("id: {}, logout error", connectionId, e);
        }
        try {
            logger.info("id: {}, process destroy, pid: {}", connectionId, pid);
            ptyProcess.destroy();
        } catch (Exception e){
            logger.error("id: {}, close connection error", connectionId, e);
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
			logger.info("id: {}, {} start", connectionId, Thread.currentThread().getName());
            char[] buffer = new char[1024];
            try {
                while (!Thread.interrupted()) {
                    int len = reader.read(buffer);
                    if(len == -1) {
                        break;
                    }
					synchronized (outputBuffer) {
						int fromIndex = outputBuffer.length();
						outputBuffer.append(buffer, 0, len);
						boolean failed = matchFlags(currentCommandConfigurer.getFailFlags(), fromIndex);
						boolean success = ArrayUtils.isEmpty(currentCommandConfigurer.getSuccessFlags()) && ArrayUtils.isEmpty(currentCommandConfigurer.getFailFlags());
						if(!failed) {
							success = matchFlags(currentCommandConfigurer.getSuccessFlags(), fromIndex);
						}
						if(success | failed) {
							String output = outputBuffer.substring(fromIndex);
							currentCommandResult = success ? CommandResult.successfulResult(output) : CommandResult.failedResult(output);
						}
					}
                }
            } catch (IOException e) {
                logger.error("id: {}, ShellOutputReader read exception", connectionId, e);
            }
			logger.info("id: {}, {} Terminated", connectionId, Thread.currentThread().getName());
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
			logger.info("id: {}, {} start", connectionId, Thread.currentThread().getName());
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
								logger.info("id: {}, pid: {}, keepAliveResult: {}", connectionId, pid, commandResult);
                                lastSendTime = tempLastSendTime;
                            }
                        }
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                logger.error("id: {}, {}", connectionId, Thread.currentThread().getName(), e);
                if (!isConnected()) {
					logger.error("id: {}, {} Exit, Connection Closed", connectionId, Thread.currentThread().getName());
                }
            }
			logger.info("id: {}, {} Terminated", connectionId, Thread.currentThread().getName());
        }
    }
}
