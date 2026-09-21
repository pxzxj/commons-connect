package io.github.pxzxj.connect.factory.impl;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.CommandResult;
import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.ConnectionFactory;
import io.github.pxzxj.connect.GeneralConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a dummy telnet server inside the test process and covers the socket connection, the login wait, the
 * command echo and the pre disconnect command end to end, without any external device. TelnetClient only
 * negotiates options registered through TelnetOptionHandler and this project registers none, so the server
 * does not have to answer IAC negotiation bytes.
 */
@Timeout(60)
class TelnetConnectionFactoryEmbeddedServerTest {

	private static final String HOST = "127.0.0.1";

	private static final String BANNER_PREFIX = "Welcome to dummy telnet server\r\n";

	private static final String DEFAULT_PROMPT = "fake$ ";

	private static final int TIMEOUT = 5000;

	private ServerSocket serverSocket;

	private Socket acceptedSocket;

	private Thread serverThread;

	private int port;

	private List<String> receivedLines;

	private List<String> receivedInputs;

	private Charset deviceCharset = StandardCharsets.UTF_8;

	private String prompt = DEFAULT_PROMPT;

	@BeforeEach
	void startDummyTelnetServer() throws Exception {
		receivedLines = new CopyOnWriteArrayList<>();
		receivedInputs = new CopyOnWriteArrayList<>();
		serverSocket = new ServerSocket(0, 1, InetAddress.getByName(HOST));
		port = serverSocket.getLocalPort();
		serverThread = new Thread(this::serve);
		serverThread.setName("dummy-telnet-server");
		serverThread.setDaemon(true);
		serverThread.start();
	}

	@AfterEach
	void stopDummyTelnetServer() throws Exception {
		closeQuietly(acceptedSocket);
		serverSocket.close();
		serverThread.interrupt();
	}

	@Test
	void createConnection_completesHandshakeAndLogin() throws Exception {
		Connection connection = factory().createConnection(configurer());

		try {
			assertTrue(connection.isConnected());
			assertEquals(BANNER_PREFIX + DEFAULT_PROMPT, connection.getPostConnectOutput());
		}
		finally {
			connection.close();
		}

		assertFalse(connection.isConnected());
	}

	@Test
	void sendCommand_returnsEchoFromServer() throws Exception {
		Connection connection = factory().createConnection(configurer());

		try {
			CommandResult commandResult = connection.sendCommand(command("whoami"));

			assertTrue(commandResult.isSuccess(), "actual echo: " + commandResult.getResult());
			assertTrue(commandResult.getResult().contains("[whoami]"), "actual echo: " + commandResult.getResult());
			assertEquals(Arrays.asList("whoami"), receivedLines);
		}
		finally {
			connection.close();
		}
	}

	@Test
	void sendCommand_multipleCommandsShareTheSameSession() throws Exception {
		Connection connection = factory().createConnection(configurer());

		try {
			assertTrue(connection.sendCommand(command("whoami")).isSuccess());
			assertTrue(connection.sendCommand(command("pwd")).isSuccess());

			assertEquals(Arrays.asList("whoami", "pwd"), receivedLines);
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_runsPostConnectCommand() throws Exception {
		Connection connection = factory().createConnection(configurerBuilder().postConnect(command("whoami")).build());

		try {
			assertTrue(connection.getPostConnectOutput().contains(BANNER_PREFIX));
			assertTrue(connection.getPostConnectOutput().contains("[whoami]"),
					"actual echo: " + connection.getPostConnectOutput());
			assertEquals(Arrays.asList("whoami"), receivedLines);
		}
		finally {
			connection.close();
		}
	}

	@Test
	void close_sendsPreDisconnectCommandBeforeDisconnecting() throws Exception {
		Connection connection = factory().createConnection(configurerBuilder().preDisconnect(command("exit")).build());

		connection.close();

		assertEquals(Arrays.asList("exit"), receivedLines);
		assertFalse(connection.isConnected());
	}

	@Test
	void sendCommand_decodesEchoWithConfiguredCharset() throws Exception {
		deviceCharset = Charset.forName("GBK");
		prompt = "提示符> ";
		Connection connection = factory().createConnection(configurer());

		try {
			assertEquals(BANNER_PREFIX + "提示符> ", connection.getPostConnectOutput());

			CommandResult commandResult = connection.sendCommand(command("whoami"));

			assertTrue(commandResult.isSuccess(), "actual echo: " + commandResult.getResult());
			assertTrue(commandResult.getResult().contains("[whoami]"), "actual echo: " + commandResult.getResult());
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_serverNotListening_failsWithGeneralConnectionException() throws Exception {
		int unusedPort = unusedPort();

		assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(configurerBuilder().port(unusedPort).build()));
	}

	private ConnectionConfigurer configurer() {
		return configurerBuilder().build();
	}

	private ConnectionConfigurerBuilder configurerBuilder() {
		return ConnectionConfigurerBuilder.telnet()
				.id("telnet-test-1")
				.host(HOST)
				.port(port)
				.charset(deviceCharset.name())
				.successFlags(prompt)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("");
	}

	private static ConnectionFactory factory() {
		return new TelnetConnectionFactory();
	}

	private CommandConfigurer command(String command) {
		return CommandConfigurerBuilder.newCommandConfigurer(command)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(prompt)
				.build();
	}

	private static int unusedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(HOST))) {
			return socket.getLocalPort();
		}
	}

	private static void closeQuietly(Socket socket) {
		if (socket == null) {
			return;
		}
		try {
			socket.close();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void serve() {
		try {
			acceptedSocket = serverSocket.accept();
			try (InputStream in = acceptedSocket.getInputStream();
					OutputStream out = acceptedSocket.getOutputStream()) {
				handleConnection(in, out);
			}
		}
		catch (IOException e) {
			// the server is closed when the test ends, which is expected
		}
	}

	private void handleConnection(InputStream in, OutputStream out) throws IOException {
		write(out, BANNER_PREFIX + prompt);
		StringBuilder pending = new StringBuilder();
		byte[] buffer = new byte[256];
		int length;
		while ((length = in.read(buffer)) != -1) {
			String input = new String(buffer, 0, length, deviceCharset);
			receivedInputs.add(input);
			pending.append(input);
			int newline;
			while ((newline = pending.indexOf("\n")) >= 0) {
				String line = pending.substring(0, newline).trim();
				pending.delete(0, newline + 1);
				receivedLines.add(line);
				write(out, "[" + line + "]\r\n");
			}
			write(out, prompt);
		}
	}

	private void write(OutputStream out, String text) throws IOException {
		out.write(text.getBytes(deviceCharset));
		out.flush();
	}
}
