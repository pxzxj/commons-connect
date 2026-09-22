package io.github.pxzxj.connect.factory.impl;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.CommandResult;
import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.GeneralConnectionException;
import io.github.pxzxj.connect.factory.ConnectionFactory;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts an embedded SSH server inside the test process and covers the handshake, the shell channel,
 * the post connect command and the keep alive daemon end to end, without any external device.
 */
@Timeout(60)
class SshConnectionFactoryEmbeddedServerTest {

	private static final String HOST = "127.0.0.1";

	private static final String USERNAME = "tester";

	private static final String PASSWORD = "secret";

	private static final String PASSPHRASE = "key-passphrase";

	private static final String PROMPT = "fake$ ";

	private static final String LOGIN_BANNER = "Welcome to embedded sshd\r\n" + PROMPT;

	private static final int TIMEOUT = 5000;

	private SshServer sshServer;

	private int port;

	@TempDir
	Path tempDir;

	private String privateKeyPath;

	private String encryptedPrivateKeyPath;

	private List<String> receivedLines;

	private List<String> receivedInputs;

	@BeforeEach
	void startEmbeddedSshServer() throws Exception {
		receivedLines = new CopyOnWriteArrayList<>();
		receivedInputs = new CopyOnWriteArrayList<>();
		privateKeyPath = writeKeyPair(KeyPair.RSA, null, "id_rsa");
		encryptedPrivateKeyPath = writeKeyPair(KeyPair.RSA, PASSPHRASE.getBytes("utf-8"), "id_rsa_encrypted");
		sshServer = SshServer.setUpDefaultServer();
		sshServer.setHost(HOST);
		sshServer.setPort(0);
		sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
		sshServer.setPasswordAuthenticator(
				(username, password, session) -> USERNAME.equals(username) && PASSWORD.equals(password));
		sshServer.setPublickeyAuthenticator((username, key, session) -> USERNAME.equals(username));
		sshServer.setShellFactory(channel -> new FakeShellCommand(receivedLines, receivedInputs));
		sshServer.start();
		port = sshServer.getPort();
	}

	private String writeKeyPair(int type, byte[] passphrase, String fileName) throws Exception {
		KeyPair keyPair = KeyPair.genKeyPair(new JSch(), type, 2048);
		String path = tempDir.resolve(fileName).toString();
		keyPair.writePrivateKey(path, passphrase);
		return path;
	}

	/**
	 * Writes every given host key as a known_hosts entry. The server uses a non standard port, so the entries
	 * have to use the {@code [host]:port} form that JSch looks up.
	 */
	private Path writeKnownHosts(Iterable<? extends java.security.KeyPair> hostKeys) throws Exception {
		List<String> lines = new ArrayList<>();
		for (java.security.KeyPair hostKey : hostKeys) {
			lines.add("[" + HOST + "]:" + port + " " + PublicKeyEntry.toString(hostKey.getPublic()));
		}
		return Files.write(tempDir.resolve("known_hosts"), lines);
	}

	@AfterEach
	void stopEmbeddedSshServer() throws Exception {
		sshServer.stop(true);
	}

	@Test
	void createConnection_completesHandshakeAndLogin() throws Exception {
		Connection connection = factory().createConnection(configurer());

		try {
			assertTrue(connection.isConnected());
			assertEquals(LOGIN_BANNER, connection.getPostConnectOutput());
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
			assertTrue(connection.getPostConnectOutput().contains(LOGIN_BANNER));
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
	void createConnection_keepAliveDaemonKeepsSendingKeepAliveCommand() throws Exception {
		Connection connection = factory().createConnection(configurerBuilder()
				.keepAliveCommand("whoami")
				.keepAliveWaitStr(PROMPT)
				.keepAliveWaitTimeout(TIMEOUT)
				.keepAliveInterval(500)
				.build());

		try {
			assertTrue(awaitInput("whoami"), "keep alive command was not received by the server");
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_strictHostKeyCheckingWithMatchingKnownHosts_connects() throws Exception {
		Path knownHosts = writeKnownHosts(sshServer.getKeyPairProvider().loadKeys(null));

		Connection connection = factory().createConnection(configurerBuilder()
				.knownHosts(knownHosts.toString())
				.strictHostKeyChecking(true)
				.build());

		try {
			assertTrue(connection.isConnected());
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_strictHostKeyCheckingWithoutKnownHosts_fails() {
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(configurerBuilder().strictHostKeyChecking(true).build()));

		assertTrue(exception.getMessage().contains("create ssh connection error"),
				"actual message: " + exception.getMessage());
		assertTrue(exception.getCause().getMessage().contains("reject HostKey"),
				"actual cause: " + exception.getCause().getMessage());
	}

	@Test
	void createConnection_strictHostKeyCheckingWithChangedHostKey_fails() throws Exception {
		Path knownHosts = writeKnownHosts(new SimpleGeneratorHostKeyProvider().loadKeys(null));

		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(configurerBuilder()
						.knownHosts(knownHosts.toString())
						.strictHostKeyChecking(true)
						.build()));

		assertTrue(exception.getCause().getMessage().contains("HostKey has been changed"),
				"actual cause: " + exception.getCause().getMessage());
	}

	@Test
	void createConnection_wrongPassword_failsWithGeneralConnectionException() {
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(configurerBuilder().password("wrong-password").build()));

		assertTrue(exception.getMessage().contains("create ssh connection error"),
				"actual message: " + exception.getMessage());
		assertTrue(exception.getCause().getMessage().contains("Auth fail"),
				"actual cause: " + exception.getCause().getMessage());
	}

	@Test
	void createConnection_withPrivateKey_authenticatesWithoutPassword() throws Exception {
		Connection connection = factory().createConnection(publicKeyConfigurerBuilder()
				.privateKey(privateKeyPath)
				.postConnect(command("whoami"))
				.build());

		try {
			assertTrue(connection.isConnected());
			assertTrue(connection.getPostConnectOutput().contains("[whoami]"),
					"actual echo: " + connection.getPostConnectOutput());
			assertEquals(Arrays.asList("whoami"), receivedLines);
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_withEncryptedPrivateKey_authenticates() throws Exception {
		Connection connection = factory().createConnection(publicKeyConfigurerBuilder()
				.privateKey(encryptedPrivateKeyPath)
				.passphrase(PASSPHRASE)
				.build());

		try {
			assertTrue(connection.isConnected());
		}
		finally {
			connection.close();
		}
	}

	@Test
	void createConnection_encryptedPrivateKeyWithWrongPassphrase_fails() {
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(publicKeyConfigurerBuilder()
						.privateKey(encryptedPrivateKeyPath)
						.passphrase("wrong-passphrase")
						.build()));

		// jsch swallows the wrong passphrase in addIdentity, the failure only shows up as an authentication failure
		assertTrue(exception.getCause().getMessage().contains("USERAUTH fail"),
				"actual cause: " + exception.getCause().getMessage());
	}

	@Test
	void createConnection_encryptedPrivateKeyWithoutPassphrase_fails() {
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class,
				() -> factory().createConnection(publicKeyConfigurerBuilder()
						.privateKey(encryptedPrivateKeyPath)
						.build()));

		assertTrue(exception.getCause().getMessage().contains("USERAUTH fail"),
				"actual cause: " + exception.getCause().getMessage());
	}

	@Test
	void createConnection_unencryptedPrivateKeyWithPassphrase_authenticates() throws Exception {
		Connection connection = factory().createConnection(publicKeyConfigurerBuilder()
				.privateKey(privateKeyPath)
				.passphrase("passphrase-of-an-unencrypted-key")
				.build());

		try {
			assertTrue(connection.isConnected());
		}
		finally {
			connection.close();
		}
	}

	private ConnectionConfigurer configurer() {
		return configurerBuilder().build();
	}

	private ConnectionConfigurerBuilder publicKeyConfigurerBuilder() {
		return ConnectionConfigurerBuilder.ssh()
				.host(HOST)
				.port(port)
				.username(USERNAME)
				.successFlags(PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("");
	}

	private ConnectionConfigurerBuilder configurerBuilder() {
		return ConnectionConfigurerBuilder.ssh()
				.host(HOST)
				.port(port)
				.username(USERNAME)
				.password(PASSWORD)
				.successFlags(PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("");
	}

	private static ConnectionFactory factory() {
		return new SshConnectionFactory();
	}

	private static CommandConfigurer command(String command) {
		return CommandConfigurerBuilder.newCommandConfigurer(command)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build();
	}

	private boolean awaitInput(String text) throws InterruptedException {
		// ConnectionBase.KeepAliveDaemon polls every 5 seconds, so the first keep alive command is sent
		// about 5 seconds after login and the waiting window has to be wider than that
		for (int i = 0; i < 100; i++) {
			if (String.join("", receivedInputs).contains(text)) {
				return true;
			}
			TimeUnit.MILLISECONDS.sleep(100);
		}
		return false;
	}

	/**
	 * Minimal byte oriented shell: prints a login banner, answers every input chunk with a prompt and echoes
	 * every complete line as {@code [line]}. Keep alive commands carry no newline (enter is an empty string),
	 * only this implementation can receive them.
	 */
	private static class FakeShellCommand implements Command {

		private final List<String> receivedLines;

		private final List<String> receivedInputs;

		private InputStream inputStream;

		private OutputStream outputStream;

		private ExitCallback exitCallback;

		private Thread runner;

		FakeShellCommand(List<String> receivedLines, List<String> receivedInputs) {
			this.receivedLines = receivedLines;
			this.receivedInputs = receivedInputs;
		}

		@Override
		public void setInputStream(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public void setOutputStream(OutputStream outputStream) {
			this.outputStream = outputStream;
		}

		@Override
		public void setErrorStream(OutputStream errorStream) {
		}

		@Override
		public void setExitCallback(ExitCallback exitCallback) {
			this.exitCallback = exitCallback;
		}

		@Override
		public void start(ChannelSession channel, Environment env) {
			runner = new Thread(this::handleInput);
			runner.setName("fake-shell");
			runner.setDaemon(true);
			runner.start();
		}

		@Override
		public void destroy(ChannelSession channel) {
			if (runner != null) {
				runner.interrupt();
			}
		}

		private void handleInput() {
			try {
				write(LOGIN_BANNER);
				Reader reader = new InputStreamReader(inputStream, "utf-8");
				StringBuilder pending = new StringBuilder();
				char[] buffer = new char[256];
				int length;
				while ((length = reader.read(buffer)) != -1) {
					String input = new String(buffer, 0, length);
					receivedInputs.add(input);
					pending.append(input);
					int newline;
					while ((newline = pending.indexOf("\n")) >= 0) {
						String line = pending.substring(0, newline).trim();
						pending.delete(0, newline + 1);
						receivedLines.add(line);
						write("[" + line + "]\r\n");
					}
					write(PROMPT);
				}
				exitCallback.onExit(0);
			}
			catch (IOException e) {
				exitCallback.onExit(1, e.getMessage());
			}
		}

		private void write(String text) throws UnsupportedEncodingException, IOException {
			outputStream.write(text.getBytes("utf-8"));
			outputStream.flush();
		}
	}
}
