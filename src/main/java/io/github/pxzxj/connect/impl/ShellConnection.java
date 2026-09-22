package io.github.pxzxj.connect.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

import com.pty4j.PtyProcess;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.CommandResult;
import io.github.pxzxj.connect.Connection;

import io.github.pxzxj.connect.ConnectionConfigurer;

import io.github.pxzxj.connect.GeneralCommandException;
import io.github.pxzxj.connect.GeneralConnectionException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * inputStream.available() does not work for a pty, so the output is read with a separate implementation, see the <a href="https://github.com/JetBrains/pty4j/pull/37">PR</a>
 */
public class ShellConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnection.class);

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

    public ShellConnection(ConnectionConfigurer connectionConfigurer, PtyProcess ptyProcess) throws UnsupportedEncodingException {
        this.lastSendTime = System.currentTimeMillis();
        this.connectionConfigurer = connectionConfigurer;
        this.ptyProcess = ptyProcess;
        this.pid = ptyProcess.pid();
        this.reader = new InputStreamReader(ptyProcess.getInputStream(), connectionConfigurer.getCharset());
        this.writer = new OutputStreamWriter(ptyProcess.getOutputStream(), connectionConfigurer.getCharset());
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
		currentCommandResult = ArrayUtils.isNotEmpty(connectionConfigurer.getSuccessFlags()) ? CommandResult.failResult("") : CommandResult.successfulResult("");
        outputReaderThread = new Thread(new ShellOutputReader());
        outputReaderThread.setName("ShellOutputReader-" + pid);
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
        waitForString();
        if(!currentCommandResult.isSuccess()) {
            if(isConnected()){
                close();
            }
            throw new GeneralConnectionException("connection failed, output: " + outputBuffer);
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
                String message = "postConnect command failed: " + commandResult.getFailCommand() + "\n output: " + commandResult.getResult();
                throw new GeneralCommandException(message, commandResult);
            }
            postConnectOutput += commandResult.getResult();
        }
		logger.info("proc: {}, postConnectOutput: {}", pid, postConnectOutput);
        if(StringUtils.isNotEmpty(connectionConfigurer.getKeepAliveCommand())){
            keepAliveThread = new Thread(new KeepAliveDaemon());
            keepAliveThread.setName("KeepAliveDaemon-" + pid);
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();
        }
    }

    private void sendString(String command, String enter) {
        try {
            command += enter;
			logger.debug("proc: {}, send command: {}", pid, command);
            lastSendTime = System.currentTimeMillis();
            writer.write(command);
            writer.flush();
        } catch (IOException e) {
			logger.error("proc: {}, command send fail: {}", pid, command, e);
            throw new GeneralCommandException("command send fail: " + command, e);
        }
    }

    @Override
    public synchronized CommandResult sendCommand(CommandConfigurer commandConfigurer) {
        CommandConfigurer cc = commandConfigurer;
		if (cc.getCommand() == null) {
			return CommandResult.failResult("first command cannot be null");
		}
		boolean success = true;
		CommandConfigurer failCommand = null;
		// The order matters: the volatile write of offset happens-before the setLength below (a monitor operation),
		// and the reader acquires the same monitor to call length(), so it can never observe a length of 0 together
		// with an offset left over from the previous command. Swapping these two lines would make the reader's
		// substring(offset) throw StringIndexOutOfBoundsException.
		offset = 0;
		outputBuffer.setLength(0);
        while (cc != null) {
			String lastOutput = "";
			if(outputBuffer.length() > 0){
				lastOutput = outputBuffer.substring(offset);
			}
			offset = outputBuffer.length();
            if (cc.getCommand() == null && cc.getCommandFunction() == null) {
				return CommandResult.failResult("command and commandFunction cannot be null in same time");
            }
            this.currentCommandConfigurer = cc;
            this.currentCommandResult = ArrayUtils.isNotEmpty(currentCommandConfigurer.getSuccessFlags()) ? CommandResult.failResult("") : CommandResult.successfulResult("");
            String enter = cc.getEnter();
            String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(lastOutput);
            sendString(command, enter);
            waitForString();
            if(!currentCommandResult.isSuccess()) {
				success = false;
				failCommand = cc;
                break;
            }
            cc = cc.getNext();
        }
        return success ? CommandResult.successfulResult(outputBuffer.toString()) : CommandResult.failResult(failCommand, outputBuffer.toString());
    }

    private void waitForString() {
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
            logger.error("proc: {}", pid, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connectionConfigurer.getPreDisconnect() != null) {
                CommandResult commandResult = this.sendCommand(connectionConfigurer.getPreDisconnect());
                logger.info("proc: {}, preDisconnect output: {}", pid, commandResult.getResult());
            }
            if(outputReaderThread != null) {
                outputReaderThread.interrupt();
            }
            if(keepAliveThread != null) {
                keepAliveThread.interrupt();
            }

        } catch (Exception e){
            logger.error("proc: {}, logout error", pid, e);
        }
        try {
            logger.info("proc: {}, process destroy", pid);
            ptyProcess.destroy();
        } catch (Exception e){
            logger.error("proc: {}, close connection error", pid, e);
        }
    }

    @Override
    public boolean isConnected() {
        return ptyProcess.isAlive();
    }

    private class ShellOutputReader implements Runnable {

        @Override
        public void run() {
			logger.info("{} Start", Thread.currentThread().getName());
            char[] buffer = new char[1024];
            try {
                while (!Thread.interrupted()) {
                    int len = reader.read(buffer);
                    if(len == -1) {
                        break;
                    }
					outputBuffer.append(buffer, 0, len);
					boolean failed = matchFlags(currentCommandConfigurer.getFailFlags());
					boolean success = ArrayUtils.isEmpty(currentCommandConfigurer.getSuccessFlags()) && ArrayUtils.isEmpty(currentCommandConfigurer.getFailFlags());
					if(!failed) {
						success = matchFlags(currentCommandConfigurer.getSuccessFlags());
					}
					if(success | failed) {
						String output = outputBuffer.substring(offset);
						currentCommandResult = success ? CommandResult.successfulResult(output) : CommandResult.failResult(output);
					}
                }
            } catch (IOException e) {
                logger.error("{} read exception", Thread.currentThread().getName(), e);
            }
			logger.info("{} Terminated", Thread.currentThread().getName());
        }

        private boolean matchFlags(String[] flags) {
            if(flags == null) {
                return false;
            }
            for(String flag : flags) {
                if(outputBuffer.indexOf(flag, offset) != -1) {
                    return true;
                }
            }
            return false;
        }
    }

    private class KeepAliveDaemon implements Runnable {

        @Override
        public void run() {
			logger.info("{} Start", Thread.currentThread().getName());
            CommandConfigurer keepAliveCommand = CommandConfigurerBuilder.newCommandConfigurer(connectionConfigurer.getKeepAliveCommand())
                    .enter("")
                    .successFlags(connectionConfigurer.getKeepAliveWaitStr())
                    .timeoutMilliSeconds(connectionConfigurer.getKeepAliveWaitTimeout())
                    .build();
            try {
                while (!Thread.interrupted()) {
                    if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval()) {
                        synchronized (ShellConnection.this){
                            if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval()) {
                                CommandResult commandResult = sendCommand(keepAliveCommand);
								logger.debug("proc: {}, keepAliveResult: {}", pid, commandResult);
                            }
                        }
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                logger.error(Thread.currentThread().getName(), e);
            }
			logger.info("{} Terminated", Thread.currentThread().getName());
        }
    }
}
