package io.github.pxzxj.connect.local;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.CommandResult;
import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.factory.impl.SshConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests against the sshd running inside the local WSL Ubuntu of this machine.
 *
 * Every command is written so that its output differs from the command text itself (e.g. {@code echo x-$((2+3))}
 * prints {@code x-5}), which keeps the assertions meaningful even though the pty echoes the command back.
 * The post connect command rewrites PS1 to a plain {@code "# "} so that the success flag cannot accidentally
 * match a coloured prompt or a path.
 *
 * The sshd of that machine only accepts public key authentication, hence the private key. That key has to be
 * in the PEM format (-----BEGIN RSA PRIVATE KEY-----), because com.jcraft:jsch:0.1.55 fails with
 * "invalid privatekey" on the newer OpenSSH format (-----BEGIN OPENSSH PRIVATE KEY-----) that ssh-keygen
 * produces by default. Generate one with ssh-keygen -m PEM.
 *
 * A value is resolved from the system property of the same name first, then from
 * src/test/resources/local-wsl.properties (git ignored, because it holds machine specific values and the
 * password), and finally from the default below. When the target is unreachable every test is skipped instead
 * of failing, and so is the password test when no password is configured - which makes this class harmless on
 * machines without that environment.
 */
@Timeout(60)
class LocalWslSshConnectionTest {

	private static final String CONFIG_FILE = "local-wsl.properties";

	/** Optional, git ignored file holding the machine specific values. */
	private static final Properties LOCAL_CONFIG = loadLocalConfig();

	private static final String HOST = property("local.wsl.host", "localhost");

	private static final int PORT = Integer.parseInt(property("local.wsl.port", "22"));

	private static final String USERNAME = property("local.wsl.username", "root");

	private static final String PRIVATE_KEY = property("local.wsl.privateKey",
			new File(System.getProperty("user.home"), ".ssh/commons-connect-wsl_rsa").getPath());

	private static final String PASSWORD = property("local.wsl.password", null);

	private static final String PROMPT = "# ";

	private static final int TIMEOUT = 8000;

	private static final int PAGED_TIMEOUT = 20000;

	/** Makes the prompt exactly "# " and prints a marker, so that postConnect output can be asserted. */
	private static final String POST_CONNECT_COMMAND = "P='#'; PS1=\"$P \"; echo boot-$((4*5))";

	private Connection connection;

	@BeforeEach
	void connectToLocalWsl() {
		try {
			connection = new SshConnectionFactory().createConnection(configurerBuilder().build());
		}
		catch (RuntimeException e) {
			Assumptions.abort("local WSL ssh is not reachable at " + HOST + ":" + PORT + " (" + e + ")");
		}
	}

	@AfterEach
	void closeConnection() {
		if (connection != null) {
			connection.close();
		}
	}

	@Test
	void postConnect_recordsLoginOutputAndRunsThePostConnectCommand() {
		assertTrue(connection.isConnected());
		assertTrue(connection.getPostConnectOutput().contains("boot-20"),
				"actual postConnectOutput: " + connection.getPostConnectOutput());
	}

	@Test
	void sendCommand_singleCommand_returnsItsEcho() {
		CommandResult result = connection.sendCommand(command("echo single-$((2+3))"));

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("single-5"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_customSuccessFlag_matchesTheOutputInsteadOfThePrompt() {
		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("echo flag-$((9*9))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags("flag-81")
				.build());

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("flag-81"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_chainedCommands_runInOrder() {
		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("echo chain-$((1+1))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.next("echo chain-$((2+2))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.next("echo chain-$((3+3))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build());

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("chain-2"), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("chain-4"), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("chain-6"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_separateCalls_shareTheSameShellSession() {
		assertTrue(connection.sendCommand(command("cd /etc")).isSuccess());

		CommandResult result = connection.sendCommand(command("pwd"));

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("/etc"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagMatched_returnsFailureWithTheFailingCommand() {
		CommandConfigurer failing = CommandConfigurerBuilder
				.newCommandConfigurer("cat /no-such-file-for-commons-connect")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.failFlags("No such file or directory")
				.build();

		CommandResult result = connection.sendCommand(failing);

		assertFalse(result.isSuccess());
		assertSame(failing, result.getFailCommand());
		assertTrue(result.getResult().contains("No such file or directory"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagInChain_stopsTheFollowingCommands() {
		CommandConfigurer first = CommandConfigurerBuilder
				.newCommandConfigurer("cat /no-such-file-for-commons-connect")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.failFlags("No such file or directory")
				.build();
		first.setNext(command("echo should-not-run"));

		CommandResult result = connection.sendCommand(first);

		assertFalse(result.isSuccess());
		assertSame(first, result.getFailCommand());
		assertFalse(result.getResult().contains("should-not-run"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_moreFlag_fetchesEveryPage() {
		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("i=1; while [ $i -le 120 ]; do echo page-$i; i=$((i+1)); done | more")
				.timeoutMilliSeconds(PAGED_TIMEOUT)
				.successFlags(PROMPT)
				.moreFlag("--More--")
				.moreCommand(" ")
				.build());

		assertTrue(result.isSuccess(), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("--More--"),
				"the pager prompt should have appeared, actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("page-120"),
				"the last page should have been reached, actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_commandFunction_receivesThePreviousEcho() {
		CommandConfigurer first = CommandConfigurerBuilder
				.newCommandConfigurer("echo token-$((8*8))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build();
		first.setNext(CommandConfigurerBuilder
				.newCommandConfigurer((Function<String, String>) previousEcho ->
						previousEcho.contains("token-64") ? "echo found-$((8*8))" : "echo not-found")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build());

		CommandResult result = connection.sendCommand(first);

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("found-64"), "actual echo: " + result.getResult());
	}

	@Test
	void close_runsThePreDisconnectCommand() {
		String marker = "/tmp/commons-connect-predisconnect-" + System.nanoTime();
		Connection withPreDisconnect = null;
		try {
			withPreDisconnect = new SshConnectionFactory().createConnection(configurerBuilder()
					.preDisconnect(command("echo marker-$((7*7)) > " + marker))
					.build());
			assertTrue(withPreDisconnect.isConnected());
		}
		finally {
			if (withPreDisconnect != null) {
				withPreDisconnect.close();
			}
		}

		CommandResult result = connection.sendCommand(command("cat " + marker));
		assertTrue(result.isSuccess(), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("marker-49"), "actual echo: " + result.getResult());

		connection.sendCommand(command("rm -f " + marker));
	}

	/**
	 * The password comes from local.wsl.password; it is skipped when no password is configured. Note that the
	 * sshd has to allow password authentication (PasswordAuthentication and PermitRootLogin in sshd_config).
	 */
	@Test
	void createConnection_withPassword_authenticates() {
		Assumptions.assumeTrue(PASSWORD != null, "configure local.wsl.password (in " + CONFIG_FILE
				+ " or via -Dlocal.wsl.password) to run the password authentication test");

		Connection byPassword = new SshConnectionFactory().createConnection(ConnectionConfigurerBuilder.ssh()
				.host(HOST)
				.port(PORT)
				.username(USERNAME)
				.password(PASSWORD)
				.successFlags(PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.postConnect(CommandConfigurerBuilder.newCommandConfigurer(POST_CONNECT_COMMAND)
						.timeoutMilliSeconds(TIMEOUT)
						.successFlags(PROMPT)
						.build())
				.build());
		try {
			assertTrue(byPassword.isConnected());
		}
		finally {
			byPassword.close();
		}
	}

	private static ConnectionConfigurerBuilder configurerBuilder() {
		return ConnectionConfigurerBuilder.ssh()
				.host(HOST)
				.port(PORT)
				.username(USERNAME)
				.privateKey(PRIVATE_KEY)
				.successFlags(PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.postConnect(CommandConfigurerBuilder.newCommandConfigurer(POST_CONNECT_COMMAND)
						.timeoutMilliSeconds(TIMEOUT)
						.successFlags(PROMPT)
						.build());
	}

	private static CommandConfigurer command(String command) {
		return CommandConfigurerBuilder.newCommandConfigurer(command)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build();
	}

	private static Properties loadLocalConfig() {
		Properties properties = new Properties();
		try (InputStream in = LocalWslSshConnectionTest.class.getResourceAsStream("/" + CONFIG_FILE)) {
			if (in != null) {
				properties.load(in);
			}
		}
		catch (IOException e) {
			// the file is optional, the defaults are used when it cannot be read
		}
		return properties;
	}

	private static String property(String name, String defaultValue) {
		String value = System.getProperty(name);
		if (value == null || value.isEmpty()) {
			value = LOCAL_CONFIG.getProperty(name);
		}
		return value == null || value.isEmpty() ? defaultValue : value;
	}

}
