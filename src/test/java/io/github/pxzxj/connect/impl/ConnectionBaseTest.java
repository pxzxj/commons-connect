package io.github.pxzxj.connect.impl;

import io.github.pxzxj.connect.*;
import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionBaseTest {

	private static final int TIMEOUT = 400;

	private static final int STAGED_TIMEOUT = 1500;

	@Test
	void sendString_appendsEnterAndWritesToStream() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), emptyInput(), out);

		connection.sendString("echo hi", "\n");

		assertEquals("echo hi\n", out.toString("utf-8"));
	}

	@Test
	void sendString_outputStreamErrorIsWrappedInGeneralCommandException() throws Exception {
		TestConnection connection = new TestConnection(configurer("ok>"), emptyInput(), brokenOutput());

		assertThrows(GeneralCommandException.class, () -> connection.sendString("ls", "\n"));
	}

	@Test
	void sendCommand_successFlagMatched_returnsFullEcho() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("root@host:~# "),
				input("$ echo hi\r\nhi\r\nroot@host:~# "), out);

		CommandResult commandResult = connection.sendCommand(
				baseCommand("echo hi").successFlags("root@host:~# ").build());

		assertTrue(commandResult.isSuccess());
		assertEquals("$ echo hi\r\nhi\r\nroot@host:~# ", commandResult.getResult());
		assertEquals("echo hi\n", out.toString("utf-8"));
		assertNull(commandResult.getFailCommand());
	}

	@Test
	void sendCommand_failFlagMatched_returnsFailureAndFailCommand() throws Exception {
		TestConnection connection = new TestConnection(configurer("root@host:~# "),
				input("$ badcmd\r\nbadcmd: command not found\r\n"), new ByteArrayOutputStream());
		CommandConfigurer commandConfigurer = baseCommand("badcmd")
				.successFlags("root@host:~# ")
				.failFlags("command not found")
				.build();

		CommandResult commandResult = connection.sendCommand(commandConfigurer);

		assertFalse(commandResult.isSuccess());
		assertSame(commandConfigurer, commandResult.getFailCommand());
	}

	@Test
	void sendCommand_successAndFailFlagMatchedInSameRead_fails() throws Exception {
		TestConnection connection = new TestConnection(configurer("other# "),
				input("error: boom\r\ndone>"), new ByteArrayOutputStream());

		CommandResult commandResult = connection.sendCommand(
				baseCommand("run").successFlags("done>").failFlags("error").build());

		assertFalse(commandResult.isSuccess());
		assertEquals("error: boom\r\ndone>", commandResult.getResult());
	}

	@Test
	void sendCommand_firstCommandIsNull_returnsFailedResultWithoutSending() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), emptyInput(), out);

		CommandResult commandResult = connection.sendCommand(
				CommandConfigurerBuilder.newCommandConfigurer((String) null).build());

		assertFalse(commandResult.isSuccess());
		assertEquals("first command cannot be null", commandResult.getResult());
		assertEquals("", out.toString("utf-8"));
	}

	@Test
	void sendCommand_nextCommandWithoutCommandAndFunction_returnsFailure() throws Exception {
		TestConnection connection = new TestConnection(configurer("ok>"), input("ok>"), new ByteArrayOutputStream());
		CommandConfigurer first = baseCommand("echo hi").successFlags("ok>").build();
		first.setNext(new CommandConfigurer());

		CommandResult commandResult = connection.sendCommand(first);

		assertFalse(commandResult.isSuccess());
		assertEquals("command and commandFunction cannot both be null", commandResult.getResult());
	}

	@Test
	void sendCommand_flagNeverMatched_failsAfterTimeout() throws Exception {
		TestConnection connection = new TestConnection(configurer("never>"),
				input("nothing here"), new ByteArrayOutputStream());

		long start = System.currentTimeMillis();
		CommandResult commandResult = connection.sendCommand(baseCommand("ls").successFlags("never>").build());
		long cost = System.currentTimeMillis() - start;

		assertFalse(commandResult.isSuccess());
		assertEquals("nothing here", commandResult.getResult());
		assertTrue(cost >= TIMEOUT - 150, "should wait close to the timeout, actual " + cost + "ms");
		assertTrue(cost < TIMEOUT + 2000, "waited far longer than the timeout, actual " + cost + "ms");
	}

	@Test
	void sendCommand_chainStopsAtFailedCommand() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), input("denied\r\n"), out);
		CommandConfigurer first = baseCommand("first").successFlags("ok>").failFlags("denied").build();
		first.setNext(baseCommand("second").successFlags("ok>").build());

		CommandResult commandResult = connection.sendCommand(first);

		assertFalse(commandResult.isSuccess());
		assertSame(first, commandResult.getFailCommand());
		assertFalse(out.toString("utf-8").contains("second"));
	}

	@Test
	void sendCommand_commandFunctionReceivesPreviousEcho() throws Exception {
		PipedInputStream in = new PipedInputStream(8192);
		PipedOutputStream device = new PipedOutputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), in, out);
		CommandConfigurer first = baseCommand("list").timeoutMilliSeconds(STAGED_TIMEOUT).successFlags("ok>").build();
		first.setNext(CommandConfigurerBuilder
				.newCommandConfigurer((Function<String, String>) echo -> "reply-" + echo.trim())
				.enter(CommandConfigurer.BACKSLASH_N)
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("bye>")
				.build());
		deviceResponds(device, "hello ok>", "bye>");

		CommandResult commandResult = connection.sendCommand(first);

		assertTrue(commandResult.isSuccess());
		assertEquals("hello ok>bye>", commandResult.getResult());
		assertTrue(out.toString("utf-8").contains("reply-hello ok>\n"));
	}

	@Test
	void sendCommand_moreFlagSendsMoreCommandWhileWaiting() throws Exception {
		PipedInputStream in = new PipedInputStream(8192);
		PipedOutputStream device = new PipedOutputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), in, out);
		deviceResponds(device, "--More--", "ok>");

		CommandResult commandResult = connection.sendCommand(baseCommand("cat big")
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("ok>")
				.moreFlag("--More--")
				.moreCommand(" ")
				.build());

		assertTrue(commandResult.isSuccess());
		assertEquals("--More--ok>", commandResult.getResult());
		assertTrue(out.toString("utf-8").startsWith("cat big\n "));
	}

	@Test
	void sendCommand_decodesEchoWithConfiguredCharset() throws Exception {
		io.github.pxzxj.connect.ConnectionConfigurer configurer = ConnectionConfigurerBuilder.type(ConnectionConfigurer.TYPE_TELNET)
				.charset("GBK")
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.build();
		TestConnection connection = new TestConnection(configurer,
				new ByteArrayInputStream("中文ok>".getBytes("GBK")), new ByteArrayOutputStream());

		CommandResult commandResult = connection.sendCommand(baseCommand("ls").successFlags("ok>").build());

		assertTrue(commandResult.isSuccess());
		assertEquals("中文ok>", commandResult.getResult());
	}

	@Test
	void sendCommand_inputStreamErrorIsWrappedInGeneralCommandException() throws Exception {
		TestConnection connection = new TestConnection(configurer("ok>"), brokenInput(), new ByteArrayOutputStream());

		assertThrows(GeneralCommandException.class,
				() -> connection.sendCommand(baseCommand("ls").successFlags("ok>").build()));
	}

	@Test
	void sendCommand_commandsWithoutFlagsAreSentWithoutJudgingResult() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("ok>"), emptyInput(), out);

		CommandResult commandResult = connection.sendCommand(baseCommand("ls").build());

		assertTrue(commandResult.isSuccess());
		assertEquals("ls\n", out.toString("utf-8"));
	}

	@Test
	void postConnect_successFlagMatched_recordsLoginOutput() throws Exception {
		TestConnection connection = new TestConnection(configurer("bash$ "),
				input("Welcome\r\nbash$ "), new ByteArrayOutputStream());

		connection.postConnect();

		Assertions.assertEquals("Welcome\r\nbash$ ", connection.getPostConnectOutput());
	}

	@Test
	void postConnect_loginFailed_closesConnectionAndThrows() throws Exception {
		TestConnection connection = new TestConnection(configurer("bash$ "),
				input("Login incorrect\r\n"), new ByteArrayOutputStream());

		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class, connection::postConnect);

		assertTrue(exception.getMessage().startsWith("connection failed"));
		assertTrue(exception.getMessage().contains("Login incorrect"));
		assertEquals(1, connection.getCloseCount());
	}

	@Test
	void postConnect_noFlagConfigured_waitsForDefaultFlagWithoutFailing() throws Exception {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.ssh()
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.build();
		TestConnection connection = new TestConnection(configurer, input("Welcome\r\n"), new ByteArrayOutputStream());

		long start = System.currentTimeMillis();
		connection.postConnect();
		long cost = System.currentTimeMillis() - start;

		assertEquals(0, connection.getCloseCount());
		assertTrue(cost >= TIMEOUT - 150, "should wait for the default flag until it times out, actual " + cost + "ms");
	}

	@Test
	void postConnect_postConnectCommand_echoIsAppended() throws Exception {
		PipedInputStream in = new PipedInputStream(8192);
		PipedOutputStream device = new PipedOutputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.ssh()
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.keepAliveCommand("")
				.successFlags("bash$ ")
				.postConnect(baseCommand("whoami").timeoutMilliSeconds(STAGED_TIMEOUT).successFlags("__DONE__").build())
				.build();
		TestConnection connection = new TestConnection(configurer, in, out);
		deviceResponds(device, "Welcome\r\nbash$ ", "__DONE__");

		connection.postConnect();

		Assertions.assertEquals("Welcome\r\nbash$ __DONE__", connection.getPostConnectOutput());
		assertTrue(out.toString("utf-8").contains("whoami\n"));
	}

	@Test
	void postConnect_postConnectCommandFailed_closesConnectionAndThrows() throws Exception {
		PipedInputStream in = new PipedInputStream(8192);
		PipedOutputStream device = new PipedOutputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.ssh()
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.keepAliveCommand("")
				.successFlags("bash$ ")
				.postConnect(baseCommand("whoami").timeoutMilliSeconds(STAGED_TIMEOUT).failFlags("denied").build())
				.build();
		TestConnection connection = new TestConnection(configurer, in, out);
		deviceResponds(device, "Welcome\r\nbash$ ", "denied\r\n");

		GeneralCommandException exception = assertThrows(GeneralCommandException.class, connection::postConnect);

		assertTrue(exception.getMessage().startsWith("post connect command failed"));
		assertEquals(1, connection.getCloseCount());
	}

	@Test
	void close_withoutPreDisconnectCommand_doesNotSendAnything() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestConnection connection = new TestConnection(configurer("bash$ "), input("Welcome\r\nbash$ "), out);
		connection.postConnect();

		assertDoesNotThrow(connection::close);

		assertEquals(1, connection.getCloseCount());
		assertFalse(connection.isConnected());
		assertEquals("", out.toString("utf-8"));
	}

	private static ConnectionConfigurer configurer(String... successFlags) {
		return ConnectionConfigurerBuilder.type(ConnectionConfigurer.TYPE_TELNET)
				.successFlags(successFlags)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.build();
	}

	private static CommandConfigurerBuilder baseCommand(String command) {
		// an explicit LF keeps these tests platform independent, the default enter differs between platforms
		return CommandConfigurerBuilder.newCommandConfigurer(command).enter(CommandConfigurer.BACKSLASH_N)
				.timeoutMilliSeconds(TIMEOUT);
	}

	private static InputStream input(String text) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(text.getBytes("utf-8"));
	}

	private static InputStream emptyInput() {
		return new ByteArrayInputStream(new byte[0]);
	}

	private static InputStream brokenInput() {
		return new InputStream() {

			@Override
			public int available() throws IOException {
				throw new IOException("device unavailable");
			}

			@Override
			public int read() {
				return -1;
			}
		};
	}

	private static OutputStream brokenOutput() {
		return new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				throw new IOException("device write fail");
			}
		};
	}

	private static void deviceResponds(PipedOutputStream device, String... chunks) {
		Thread thread = new Thread(() -> {
			try {
				for (String chunk : chunks) {
					TimeUnit.MILLISECONDS.sleep(400);
					device.write(chunk.getBytes("utf-8"));
					device.flush();
				}
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		thread.setName("test-device");
		thread.setDaemon(true);
		thread.start();
	}

	private static class TestConnection extends ConnectionBase {

		private int closeCount;

		private boolean connected = true;

		TestConnection(ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream)
				throws UnsupportedEncodingException {
			super(connectionConfigurer, inputStream, outputStream);
		}

		int getCloseCount() {
			return closeCount;
		}

		@Override
		public boolean isConnected() {
			return connected;
		}

		@Override
		public synchronized void close() {
			closeCount++;
			connected = false;
			preDisconnect();
		}
	}
}
