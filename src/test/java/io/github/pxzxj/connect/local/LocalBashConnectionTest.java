package io.github.pxzxj.connect.local;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.CommandConfigurerBuilder;
import io.github.pxzxj.connect.CommandResult;
import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.factory.impl.ShellConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests that drive the bash of the WSL Ubuntu of this machine through {@link ShellConnectionFactory}.
 *
 * The shell is started with {@code ubuntu2004.exe}, the launcher that Windows installs for that distribution: it
 * always enters Ubuntu-20.04 whatever the default distribution is ({@code wsl.exe} without arguments would pick
 * the default one, which here is docker-desktop-data and has no {@code /bin/bash} at all), and it accepts no
 * extra arguments, which suits the single argument {@code shellPath} of the factory.
 *
 * Being Linux, this shell takes the default {@code \n} as the enter key, unlike cmd.exe and powershell.exe which
 * need {@code \r} (see {@link LocalShellConnectionTest} and the README section about enter). The locale of that
 * distribution is Chinese, so the commands whose error text is asserted are prefixed with {@code LC_ALL=C}.
 *
 * Values can be overridden with -Dlocal.bash.path. When the launcher cannot be started every test is skipped
 * instead of failing, so this class is harmless on machines without that distribution.
 */
@Timeout(60)
class LocalBashConnectionTest {

	private static final String SHELL_PATH = System.getProperty("local.bash.path", "ubuntu2004.exe");

	private static final String CHARSET = "utf-8";

	/**
	 * A bash prompt ends with "$ " for a normal user and "# " for root. Only that single character is used: winpty
	 * drops trailing blanks when it repaints a line, so a prompt that ends with a space never arrives with it.
	 * Both are accepted because this class runs under a normal user when driven from Windows (through the WSL
	 * launcher) and under root when the suite itself runs inside WSL.
	 */
	private static final String[] LOGIN_PROMPT = new String[]{"$", "#"};

	/** Prompt installed by the post connect command; it is the success flag of every command afterwards. */
	private static final String PROMPT = "PXY>";

	private static final int TIMEOUT = 8000;

	private static final int PAGED_TIMEOUT = 20000;

	/**
	 * Builds the prompt from a variable, so that the literal "PXY> " does not appear in the command text: the pty
	 * echoes the command back and would otherwise satisfy the success flag before the command has even run. The
	 * marker uses shell arithmetic so that it does not depend on any environment variable.
	 */
	private static final String POST_CONNECT_COMMAND = "A=PXY; PS1=\"$A>\"; echo boot-$((4*5))";

	@TempDir
	Path tempDir;

	private Connection connection;

	@BeforeEach
	void connectToWslBash() {
		connection = connect();
	}

	@AfterEach
	void closeConnection() {
		if (connection != null) {
			connection.close();
		}
	}

	@Test
	void postConnect_setsThePromptAndRunsThePostConnectCommand() {
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
				.newCommandConfigurer("export CHAIN_MARK=v-$((5+5))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.next("echo $CHAIN_MARK")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build());

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("v-10"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_separateCalls_shareTheSameShellSession() {
		assertTrue(connection.sendCommand(command("export CC_MARK=v-$((3*4))")).isSuccess());

		CommandResult result = connection.sendCommand(command("echo $CC_MARK"));

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("v-12"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagMatched_returnsFailureWithTheFailingCommand() {
		CommandConfigurer failing = CommandConfigurerBuilder
				.newCommandConfigurer("echo FAILED-$((1+1))")
				.timeoutMilliSeconds(TIMEOUT)
				// the prompt cannot be the success flag here: a prompt left over from the previous command (or
				// repainted by readline) can be read back before this command has produced any output at all,
				// which would make the result look successful without the fail flag ever being checked
				.successFlags("__NEVER_PRINTED__")
				.failFlags("FAILED-2")
				.build();

		CommandResult result = connection.sendCommand(failing);

		assertFalse(result.isSuccess());
		assertSame(failing, result.getFailCommand());
		assertTrue(result.getResult().contains("FAILED-2"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagMatchedWhenAPromptFollowsInALaterReadBlock() {
		CommandConfigurer failing = CommandConfigurerBuilder
				.newCommandConfigurer("LC_ALL=C ls /nope")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.failFlags("No such")
				.build();

		CommandResult result = connection.sendCommand(failing);

		assertFalse(result.isSuccess());
		assertSame(failing, result.getFailCommand());
		assertTrue(result.getResult().contains("No such"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagInChain_stopsTheFollowingCommands() {
		CommandConfigurer first = CommandConfigurerBuilder
				.newCommandConfigurer("echo FAILED-$((1+1))")
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags("__NEVER_PRINTED__")
				.failFlags("FAILED-2")
				.build();
		first.setNext(command("echo should-not-run"));

		CommandResult result = connection.sendCommand(first);

		assertFalse(result.isSuccess());
		assertSame(first, result.getFailCommand());
		assertFalse(result.getResult().contains("should-not-run"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_moreFlag_fetchesEveryPage() throws IOException {
		// more writes its pages to a terminal that WSL relays to winpty, and that bridge forwards screen changes
		// instead of the byte stream, so the page content never reaches the pty - the same reason why the cmd.exe
		// counterpart is skipped. Override local.bash.path with /bin/bash on a Unix host to really exercise this.
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"),
				"a pager cannot be observed through winpty");

		Path pages = tempDir.resolve("pages.txt");
		List<String> lines = new ArrayList<>();
		for (int i = 1; i <= 120; i++) {
			lines.add("page-" + i);
		}
		Files.write(pages, lines, StandardCharsets.UTF_8);

		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("more " + (io.github.pxzxj.connect.support.SystemInfoRt.isWindows
						? "/mnt/c" + pages.toString().substring(2).replace('\\', '/')
						: pages.toString()))
				.timeoutMilliSeconds(PAGED_TIMEOUT)
				// the prompt is repainted by winpty while more runs, so the last page itself is the success flag
				.successFlags("page-120")
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
			withPreDisconnect = new ShellConnectionFactory().createConnection(configurerBuilder()
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

	private static Connection connect() {
		try {
			return new ShellConnectionFactory().createConnection(configurerBuilder().build());
		}
		catch (RuntimeException e) {
			return Assumptions.abort("cannot start " + SHELL_PATH + " (" + e + ")");
		}
	}

	private static ConnectionConfigurerBuilder configurerBuilder() {
		return ConnectionConfigurerBuilder.shell()
				.shellPath(SHELL_PATH)
				.charset(CHARSET)
				.successFlags(LOGIN_PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.postConnect(CommandConfigurerBuilder
						.newCommandConfigurer(POST_CONNECT_COMMAND)
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

}
