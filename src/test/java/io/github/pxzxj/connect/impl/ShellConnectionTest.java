package io.github.pxzxj.connect.impl;

import com.pty4j.PtyProcess;
import io.github.pxzxj.connect.*;
import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellConnectionTest {

	private static final int TIMEOUT = 1000;

	private static final int STAGED_TIMEOUT = 1500;

	private static final long DEVICE_DELAY_MILLI_SECONDS = 150;

	private static final String LOGIN_OUTPUT = "Welcome\r\nbash$ ";

	private PipedOutputStream device;

	private PipedInputStream deviceInput;

	private BlockingQueue<String> deviceChunks;

	private Thread deviceWriter;

	private ByteArrayOutputStream commandOutput;

	private PtyProcess ptyProcess;

	private AtomicBoolean processAlive;

	@BeforeEach
	void setUp() throws Exception {
		deviceInput = new PipedInputStream(8192);
		device = new PipedOutputStream(deviceInput);
		deviceChunks = new LinkedBlockingQueue<>();
		commandOutput = new ByteArrayOutputStream();
		processAlive = new AtomicBoolean(true);
		ptyProcess = mock(PtyProcess.class);
		when(ptyProcess.pid()).thenReturn(1234L);
		when(ptyProcess.getInputStream()).thenReturn(deviceInput);
		when(ptyProcess.getOutputStream()).thenReturn(commandOutput);
		when(ptyProcess.isAlive()).thenAnswer(invocation -> processAlive.get());
		doAnswer(invocation -> {
			processAlive.set(false);
			return null;
		}).when(ptyProcess).destroy();
		deviceWriter = new Thread(this::writeDeviceOutput);
		deviceWriter.setName("test-device");
		deviceWriter.setDaemon(true);
		deviceWriter.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		deviceChunks.clear();
		deviceWriter.interrupt();
		device.close();
	}

	@Test
	void constructor_exposesPtyProcess() throws Exception {
		ShellConnection connection = newConnection(configurer("bash$ "));

		assertSame(ptyProcess, connection.getPtyProcess());
		assertTrue(connection.isConnected());
	}

	@Test
	void postConnect_flagMatched_recordsLoginOutput() throws Exception {
		ShellConnection connection = newConnection(configurer("bash$ "));
		deviceResponds(LOGIN_OUTPUT);

		connection.postConnect();

		assertEquals(LOGIN_OUTPUT, connection.getPostConnectOutput());
	}

	@Test
	void postConnect_emptySuccessFlagsArray_connectsSuccessfully() throws Exception {
		ShellConnection connection = newConnection(configurer());
		deviceResponds("Welcome\r\n");

		connection.postConnect();

		assertEquals("Welcome\r\n", connection.getPostConnectOutput());
		assertTrue(connection.isConnected());
	}

	@Test
	void postConnect_flagNeverMatched_closesConnectionAndThrows() throws Exception {
		ShellConnection connection = newConnection(configurer("bash$ "));
		deviceResponds("Login incorrect\r\n");

		long start = System.currentTimeMillis();
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class, connection::postConnect);
		long cost = System.currentTimeMillis() - start;

		assertTrue(exception.getMessage().startsWith("connection failed"), "actual message: " + exception.getMessage());
		assertTrue(exception.getMessage().contains("Login incorrect"), "actual message: " + exception.getMessage());
		assertTrue(cost >= TIMEOUT - 150, "should wait until timeout, actual " + cost + "ms");
		verify(ptyProcess).destroy();
		assertFalse(connection.isConnected());
	}

	@Test
	void postConnect_failFlagMatched_closesConnectionAndThrows() throws Exception {
		ShellConnection connection = newConnection(shellConfigurer()
				.successFlags("bash$ ")
				.failFlags("Login incorrect")
				.build());
		deviceResponds("Login incorrect\r\n");

		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class, connection::postConnect);

		assertTrue(exception.getMessage().startsWith("connection failed"));
		verify(ptyProcess).destroy();
		assertFalse(connection.isConnected());
	}

	@Test
	void postConnect_keepAliveCommandConfigured_startsKeepAliveThreadNamedByPid() throws Exception {
		ShellConnection connection = newConnection(ConnectionConfigurerBuilder.shell()
				.keepAliveCommand("whoami")
				.timeoutMilliSeconds(TIMEOUT)
				.build());
		try {
			deviceResponds(LOGIN_OUTPUT);

			connection.postConnect();

			assertTrue(keepAliveThreadStarted("KeepAliveDaemon-" + ptyProcess.pid()));
		}
		finally {
			connection.close();
		}
	}

	@Test
	void sendCommand_flagMatched_returnsEchoOfThisCommandOnly() throws Exception {
		ShellConnection connection = login();
		deviceResponds("file1\r\nfile2\r\n__DONE__");

		CommandResult commandResult = connection.sendCommand(io.github.pxzxj.connect.CommandConfigurerBuilder.newCommandConfigurer("ls")
				.enter(io.github.pxzxj.connect.CommandConfigurer.BACKSLASH_N)
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("__DONE__")
				.build());

		assertTrue(commandResult.isSuccess());
		assertEquals("file1\r\nfile2\r\n__DONE__", commandResult.getResult());
		assertEquals("ls\n", commandOutput.toString("utf-8"));
	}

	@Test
	void sendCommand_failFlagMatched_returnsFailureAndFailCommand() throws Exception {
		ShellConnection connection = login();
		deviceResponds("No such file\r\n");
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("cat missing")
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("bash$ ")
				.failFlags("No such file")
				.build();

		CommandResult commandResult = connection.sendCommand(commandConfigurer);

		assertFalse(commandResult.isSuccess());
		assertSame(commandConfigurer, commandResult.getFailCommand());
	}

	@Test
	void sendCommand_firstCommandIsNull_returnsFailedResult() throws Exception {
		ShellConnection connection = login();

		CommandResult commandResult = connection.sendCommand(
				CommandConfigurerBuilder.newCommandConfigurer((String) null).build());

		assertFalse(commandResult.isSuccess());
		assertEquals("first command cannot be null", commandResult.getResult());
	}

	@Test
	void sendCommand_commandFunctionReceivesEchoOfPreviousCommands() throws Exception {
		ShellConnection connection = login();
		CommandConfigurer first = CommandConfigurerBuilder.newCommandConfigurer("list")
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("ok>")
				.build();
		first.setNext(CommandConfigurerBuilder.newCommandConfigurer(echo -> "second:" + echo.trim())
				.timeoutMilliSeconds(STAGED_TIMEOUT)
				.successFlags("bye>")
				.build());
		deviceAnswers("list", "hello ok>");
		deviceAnswers("second:hello ok>", "bye>");

		CommandResult commandResult = connection.sendCommand(first);

		assertTrue(commandResult.isSuccess());
		assertTrue(commandOutput.toString("utf-8").contains("second:hello ok>"),
				"commandFunction should receive the echo of the previous command, actual commands: " + commandOutput);
	}

	@Test
	void close_sendsPreDisconnectCommandAndDestroysProcess() throws Exception {
		ShellConnection connection = newConnection(shellConfigurer()
				.successFlags("bash$ ")
				.preDisconnect(CommandConfigurerBuilder.newCommandConfigurer("exit")
						.enter(CommandConfigurer.BACKSLASH_N)
						.timeoutMilliSeconds(STAGED_TIMEOUT)
						.successFlags("bye>")
						.build())
				.build());
		deviceResponds(LOGIN_OUTPUT);
		connection.postConnect();
		commandOutput.reset();
		deviceResponds("bye>");

		connection.close();

		assertEquals("exit\n", commandOutput.toString("utf-8"));
		verify(ptyProcess).destroy();
		assertFalse(connection.isConnected());
	}

	private ShellConnection login() throws Exception {
		ShellConnection connection = newConnection(configurer("bash$ "));
		deviceResponds(LOGIN_OUTPUT);
		connection.postConnect();
		assertEquals(LOGIN_OUTPUT, connection.getPostConnectOutput());
		return connection;
	}

	private ShellConnection newConnection(ConnectionConfigurer connectionConfigurer)
			throws UnsupportedEncodingException {
		return new ShellConnection(connectionConfigurer, ptyProcess);
	}

	private static ConnectionConfigurer configurer(String... successFlags) {
		return shellConfigurer().successFlags(successFlags).build();
	}

	private static ConnectionConfigurerBuilder shellConfigurer() {
		return ConnectionConfigurerBuilder.shell()
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("");
	}

	private void deviceResponds(String... chunks) {
		for (String chunk : chunks) {
			deviceChunks.add(chunk);
		}
	}

	/**
	 * Fake device that answers only after it has seen the given command, which is how a real device behaves.
	 * The response is handed to the persistent writer thread: a thread that writes to a PipedOutputStream and
	 * then dies makes PipedInputStream fail the next read with "Write end dead".
	 */
	private void deviceAnswers(String expectedCommand, String response) {
		Thread thread = new Thread(() -> {
			try {
				long deadline = System.currentTimeMillis() + 5000;
				while (System.currentTimeMillis() < deadline) {
					if (commandOutput.toString("utf-8").contains(expectedCommand)) {
						deviceResponds(response);
						return;
					}
					TimeUnit.MILLISECONDS.sleep(20);
				}
				throw new IllegalStateException("device never saw command: " + expectedCommand);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
		thread.setName("test-device-answer");
		thread.setDaemon(true);
		thread.start();
	}

	private void writeDeviceOutput() {
		try {
			while (true) {
				String chunk = deviceChunks.poll(10, TimeUnit.SECONDS);
				if (chunk == null) {
					continue;
				}
				TimeUnit.MILLISECONDS.sleep(DEVICE_DELAY_MILLI_SECONDS);
				device.write(chunk.getBytes("utf-8"));
				device.flush();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean keepAliveThreadStarted(String threadName) throws InterruptedException {
		for (int i = 0; i < 20; i++) {
			if (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> threadName.equals(t.getName()))) {
				return true;
			}
			TimeUnit.MILLISECONDS.sleep(50);
		}
		return false;
	}
}
