package io.github.pxzxj.connect;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public abstract class ConnectionBase implements Connection {

	private static final Logger logger = LoggerFactory.getLogger(ConnectionBase.class);

	private final String connectionId;
	private final ConnectionConfigurer connectionConfigurer;
	private final InputStream inputStream;
	private final OutputStream outputStream;
	private final Writer writer;
	private final long createTime;
	private volatile long lastSendTime;
	private String postConnectOutput = "";
	private volatile boolean keepAlive = true;

	public ConnectionBase(String connectionId,
			ConnectionConfigurer connectionConfigurer,
			InputStream inputStream,
			OutputStream outputStream) throws UnsupportedEncodingException {
		this.connectionId = connectionId;
		this.connectionConfigurer = connectionConfigurer;
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.writer = new OutputStreamWriter(outputStream, connectionConfigurer.getCharset());
		this.createTime = System.currentTimeMillis();
		this.lastSendTime = System.currentTimeMillis();
	}

	@Override
	public String getConnectionId() {
		return connectionId;
	}

	public synchronized void sendString(String command, String enter) {
		try {
			command += enter;
			logger.info("id: {}, send command: {}", connectionId, command);
			lastSendTime = System.currentTimeMillis();
			writer.write(command);
			writer.flush();
		}
		catch (IOException e) {
			logger.error("command send fail: {}", command, e);
			throw new GeneralCommandException("command send fail: " + command, e);
		}
	}

	@Override
	public synchronized CommandResult sendCommand(CommandConfigurer commandConfigurer) {
		CommandConfigurer cc = commandConfigurer;
		if (cc.getCommand() == null) {
			return CommandResult.failedResult("第一条命令不能为空");
		}
		CommandResult commandResult = new CommandResult(commandConfigurer);
		String echo = "";
		while (cc != null) {
			if (cc.getCommand() == null && cc.getCommandFunction() == null) {
				return CommandResult.failedResult("command和commandFunction不能同时为空");
			}
			String[] successFlags = cc.getSuccessFlags();
			String[] failFlags = cc.getFailFlags();
			int timeoutMilliSeconds = cc.getTimeoutMilliSeconds();
			String enter = cc.getEnter();
			String moreFlag = cc.getMoreFlag();
			String moreCommand = cc.getMoreCommand();
			String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(echo);
			sendString(command, enter);
			if (successFlags != null || failFlags != null) {
				CommandResult waitResult = waitForString(successFlags, failFlags, timeoutMilliSeconds, moreFlag, moreCommand);
				echo += waitResult.getResult();
				if (!waitResult.isSuccess()) {
					commandResult.setSuccess(false);
					commandResult.setFailCommand(cc);
					break;
				}
			}
			else {
				CommandResult waitResult = waitForString(new String[]{CommandConfigurer.DEFAULT_WAIT_STR}, null, timeoutMilliSeconds, moreFlag, moreCommand);
				echo += waitResult.getResult();
			}
			cc = cc.getNext();
		}
		commandResult.setResult(echo);
		return commandResult;
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
		logger.info("id: {}, begin waiting for string: {} and {}", connectionId, Arrays.toString(successFlags), Arrays.toString(failFlags));
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
						//匹配成功标识且不匹配失败标识才视作成功
						success = true;
					}
					if (failMatch || success) {
						//成功匹配后再取出流中剩余数据
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

		return success ? CommandResult.successfulResult(output) : CommandResult.failedResult(output);
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
		while (startIndex < totalData.length - length) {
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
				throw new GeneralConnectionException("连接失败, 回显内容:" + waitResult.getResult());
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
				String message = "连接后执行命令失败：" + commandResult.getFailCommand() + "\n回显内容：" + commandResult.getResult();
				throw new GeneralCommandException(message, commandResult);
			}
		}
		logger.info("id: {}, login output: {}", connectionId, postConnectOutput);
		if (StringUtils.isNotEmpty(connectionConfigurer.getKeepAliveCommand())) {
			Thread thread = new Thread(new KeepAliveDaemon());
			thread.setName("KeepAliveDaemon " + connectionId);
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

	@Override
	public ConnectionConfigurer getConnectionConfigurer() {
		return connectionConfigurer;
	}

	@Override
	public long getCreateTime() {
		return createTime;
	}

	private class KeepAliveDaemon implements Runnable {

		/**
		 * 最后一次发送保活命令时间
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
								//保活命令不应该修改lastSendTime的值，因此临时保存，保活命令发送完成后恢复
								long tempLastSendTime = lastSendTime;
								lastSendKeepAliveCommandTime = System.currentTimeMillis();
								CommandResult commandResult = sendCommand(keepAliveCommand);
								logger.info("id: {}, keepAliveResult: {}", connectionId, commandResult);
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
