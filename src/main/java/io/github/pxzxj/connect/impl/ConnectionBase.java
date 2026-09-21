package io.github.pxzxj.connect.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

public abstract class ConnectionBase implements Connection {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected final ConnectionConfigurer connectionConfigurer;
	protected final String host;
	private final InputStream inputStream;
	private final OutputStream outputStream;
	private final Writer writer;
	private volatile long lastSendTime;
	private String postConnectOutput = "";
	private volatile boolean keepAlive = true;

	public ConnectionBase(ConnectionConfigurer connectionConfigurer,
			InputStream inputStream,
			OutputStream outputStream) throws UnsupportedEncodingException {
		this.connectionConfigurer = connectionConfigurer;
		this.host = connectionConfigurer.getHost();
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.writer = new OutputStreamWriter(outputStream, connectionConfigurer.getCharset());
		this.lastSendTime = System.currentTimeMillis();
	}

	public synchronized void sendString(String command, String enter) {
		try {
			command += enter;
			logger.debug("host: {}, send command: {}", host, command);
			lastSendTime = System.currentTimeMillis();
			writer.write(command);
			writer.flush();
		}
		catch (IOException e) {
			logger.error("host: {}, command send fail: {}", host, command, e);
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
		String echo = "";
		String lastEcho = "";
		while (cc != null) {
			if (cc.getCommand() == null && cc.getCommandFunction() == null) {
				return CommandResult.failResult("command and commandFunction cannot both be null");
			}
			String[] successFlags = cc.getSuccessFlags();
			String[] failFlags = cc.getFailFlags();
			int timeoutMilliSeconds = cc.getTimeoutMilliSeconds();
			String enter = cc.getEnter();
			String moreFlag = cc.getMoreFlag();
			String moreCommand = cc.getMoreCommand();
			String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(lastEcho);
			sendString(command, enter);
			if (successFlags != null || failFlags != null) {
				CommandResult waitResult = waitForString(successFlags, failFlags, timeoutMilliSeconds, moreFlag, moreCommand);
				lastEcho = waitResult.getResult();
				echo += waitResult.getResult();
				if (!waitResult.isSuccess()) {
					success = false;
					failCommand = cc;
					break;
				}
			}
			else {
				CommandResult waitResult = waitForString(new String[]{CommandConfigurer.DEFAULT_WAIT_STR}, null, timeoutMilliSeconds, moreFlag, moreCommand);
				lastEcho = waitResult.getResult();
				echo += waitResult.getResult();
			}
			cc = cc.getNext();
		}
		return success ? CommandResult.successfulResult(echo) : CommandResult.failResult(failCommand, echo);
	}

	public synchronized CommandResult waitForString(String[] successFlags,
			String[] failFlags,
			int maxMilliSeconds,
			String moreFlag,
			String moreCommand) {
		if (successFlags == null) {
			successFlags = new String[0];
		}
		if (failFlags == null) {
			failFlags = new String[0];
		}
		long startTime = lastSendTime = System.currentTimeMillis();
		logger.info("host: {}, begin waiting for string: {} and {}", host, Arrays.toString(successFlags), Arrays.toString(failFlags));
		boolean success = false;
		byte[] totalData = new byte[0];
		String output;
		int availableSize;
		try {
			byte[][] successMatchByteArray = prepareMatchBytes(successFlags);
			byte[][] failMatchByteArray = prepareMatchBytes(failFlags);
			while (System.currentTimeMillis() - startTime <= maxMilliSeconds) {
				int totalSize = totalData.length;
				if ((availableSize = inputStream.available()) > 0) {
					byte[] data = new byte[availableSize];
					int read = inputStream.read(data);
					if (read == -1) {
						break;
					}
					totalData = ArrayUtils.addAll(totalData, data);
					boolean failMatch = matchEnd(totalData, failMatchByteArray, totalSize);
					boolean successMatch = successFlags.length == 0 || matchEnd(totalData, successMatchByteArray, totalSize);
					if (successMatch && !failMatch) {
						// only a match of a success flag without a match of a fail flag counts as success
						success = true;
					}
					if (failMatch || success) {
						// drain whatever is left in the stream once a flag has matched
						if ((availableSize = inputStream.available()) > 0) {
							data = new byte[availableSize];
							read = inputStream.read(data);
							if (read != -1) {
								totalData = ArrayUtils.addAll(totalData, data);
							}
						}
						break;
					}
				}
				else {
					if (moreFlag != null) {
						sendString(moreCommand, "");
					}
					else if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval()) {
						lastSendTime = System.currentTimeMillis();
						sendString(connectionConfigurer.getKeepAliveCommand(), "");
					}
					TimeUnit.MILLISECONDS.sleep(200);
				}
			}
			output = new String(totalData, connectionConfigurer.getCharset());
		}
		catch (Exception e) {
			throw new GeneralCommandException("Error exception accur while waitting for string", e);
		}
		lastSendTime = System.currentTimeMillis();

		return success ? CommandResult.successfulResult(output) : CommandResult.failResult(output);
	}

	private boolean matchEnd(byte[] totalData, byte[][] matchByteArray, int startIndex) {
		for (byte[] data : matchByteArray) {
			if (match(totalData, data, startIndex)) {
				return true;
			}
		}
		return false;
	}

	private boolean match(byte[] totalData, byte[] data, int startIndex) {
		boolean find = false;
		int length = data.length;
		if (startIndex >= length) {
			startIndex = startIndex - length;
		}
		byte head = data[0];
		while (startIndex <= totalData.length - length) {
			startIndex = ArrayUtils.indexOf(totalData, head, startIndex);
			if (startIndex == -1) {
				break;
			}
			else if (Arrays.equals(ArrayUtils.subarray(totalData, startIndex, startIndex + length), data)) {
				find = true;
				break;
			}
			else {
				startIndex++;
			}
		}
		return find;
	}

	private byte[][] prepareMatchBytes(String[] Flags) throws UnsupportedEncodingException {
		byte[][] matchByteArray = new byte[Flags.length][];
		int index = 0;
		for (String s : Flags) {
			matchByteArray[index++] = s.getBytes(connectionConfigurer.getCharset());
		}
		return matchByteArray;
	}

	@Override
	public String getPostConnectOutput() {
		return postConnectOutput;
	}

	public void postConnect() {
		String[] successFlags = connectionConfigurer.getSuccessFlags();
		String[] failFlags = connectionConfigurer.getFailFlags();
		int timeoutMilliSeconds = connectionConfigurer.getTimeoutMilliSeconds();

		if (successFlags != null || failFlags != null) {
			CommandResult waitResult = waitForString(successFlags, failFlags, timeoutMilliSeconds, null, null);
			postConnectOutput = waitResult.getResult();
			if (!waitResult.isSuccess()) {
				if (isConnected()) {
					close();
				}
				throw new GeneralConnectionException("connection failed, echo: " + waitResult.getResult());
			}
		}
		else {
			CommandResult waitResult = waitForString(new String[]{CommandConfigurer.DEFAULT_WAIT_STR}, null, timeoutMilliSeconds, null, null);
			postConnectOutput = waitResult.getResult();
		}
		CommandConfigurer postConnectCommand = connectionConfigurer.getPostConnect();
		if (postConnectCommand != null) {
			if (postConnectCommand.getCommand() == null && postConnectCommand.getCommandFunction() != null) {
				postConnectCommand.setCommand(postConnectCommand.getCommandFunction().apply(postConnectOutput));
			}
			CommandResult commandResult = sendCommand(postConnectCommand);
			postConnectOutput += commandResult.getResult();
			if (!commandResult.isSuccess()) {
				if (isConnected()) {
					close();
				}
				String message = "post connect command failed: " + commandResult.getFailCommand() + "\n echo: " + commandResult.getResult();
				throw new GeneralCommandException(message, commandResult);
			}
		}
		logger.info("host: {}, login output: {}", host, postConnectOutput);
		if (StringUtils.isNotEmpty(connectionConfigurer.getKeepAliveCommand())) {
			Thread thread = new Thread(new KeepAliveDaemon());
			thread.setName("KeepAliveDaemon " + host);
			thread.setDaemon(true);
			thread.start();
		}
	}

	protected synchronized void preDisconnect() {
		keepAlive = false;
		if (connectionConfigurer.getPreDisconnect() != null) {
			this.sendCommand(connectionConfigurer.getPreDisconnect());
		}
	}

	private class KeepAliveDaemon implements Runnable {

		/**
		 * Time the last keep alive command was sent
		 */
		private long lastSendKeepAliveCommandTime = System.currentTimeMillis();

		@Override
		public void run() {
			logger.info("{} start", Thread.currentThread().getName());
			try {
				CommandConfigurer keepAliveCommand = CommandConfigurerBuilder.newCommandConfigurer(connectionConfigurer.getKeepAliveCommand())
						.enter("")
						.successFlags(connectionConfigurer.getKeepAliveWaitStr())
						.timeoutMilliSeconds(connectionConfigurer.getKeepAliveWaitTimeout())
						.build();
				while (keepAlive) {
					if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval() &&
							System.currentTimeMillis() - lastSendKeepAliveCommandTime > connectionConfigurer.getKeepAliveInterval()) {
						synchronized (ConnectionBase.this) {
							if (System.currentTimeMillis() - lastSendTime > connectionConfigurer.getKeepAliveInterval() &&
									System.currentTimeMillis() - lastSendKeepAliveCommandTime > connectionConfigurer.getKeepAliveInterval()) {
								// a keep alive command must not change lastSendTime, so save it before and restore it after
								long tempLastSendTime = lastSendTime;
								lastSendKeepAliveCommandTime = System.currentTimeMillis();
								CommandResult commandResult = sendCommand(keepAliveCommand);
								logger.debug("host: {}, keepAliveResult: {}", host, commandResult);
								lastSendTime = tempLastSendTime;
							}
						}
					}
					TimeUnit.SECONDS.sleep(5);
				}
			}
			catch (Exception e) {
				logger.error(Thread.currentThread().getName(), e);
				if (!isConnected()) {
					logger.error("{} Exit, Connection Closed", Thread.currentThread().getName());
				}
			}
			logger.info("{} Terminated", Thread.currentThread().getName());
		}
	}

}
