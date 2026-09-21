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
import java.nio.charset.Charset;
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
 * End to end tests against a real local shell process started by {@link ShellConnectionFactory}: {@code cmd.exe}
 * by default, plus a few tests at the end that prove {@code powershell.exe} works through the same
 * {@code shellPath} option.
 *
 * Two things are specific to a Windows console and are done by every command here:
 * <ul>
 *     <li>the enter key has to be {@code \r}; the {@code \n} that {@link CommandConfigurer} defaults to only makes
 *         cmd.exe and powershell.exe echo the command without running it (see the README section about enter).
 *         It is set explicitly per command because the default is shared by SSH and Telnet, whose remote shells
 *         are byte streams and do expect {@code \n};</li>
 *     <li>the prompt is replaced by a plain {@code "PXY>"}, because the default prompt carries the working
 *         directory and would otherwise change while a test runs. The login itself waits for the trailing
 *         {@code ">"} of the default prompt, which cmd.exe and powershell.exe both end with.</li>
 * </ul>
 * Commands avoid putting their expected output into their own text - they let the shell expand
 * {@code %COMPUTERNAME%} / {@code $env:COMPUTERNAME} / {@code %SystemRoot%} instead - so a passing assertion
 * really proves the command ran, even though the pty echoes the command back.
 *
 * The charset defaults to the platform charset, which is what cmd.exe uses on a non English Windows. Values can
 * be overridden with -Dlocal.shell.path / -Dlocal.powershell.path. When the shell cannot be started every test
 * is skipped instead of failing, so this class is harmless on machines without these executables.
 */
@Timeout(60)
class LocalShellConnectionTest {

	private static final String SHELL_PATH = System.getProperty("local.shell.path", "cmd.exe");

	private static final String POWERSHELL_PATH = System.getProperty("local.powershell.path", "powershell.exe");

	private static final String CHARSET = Charset.defaultCharset().name();

	private static final String COMPUTER_NAME = System.getenv("COMPUTERNAME");

	/**
	 * A Windows console needs a bare carriage return, see the class comment. Never reuse this for SSH or Telnet
	 * connections: their remote shell reads a byte stream and expects a line feed there.
	 */
	private static final String ENTER = "\r";

	/** The default prompt of cmd.exe and powershell.exe both end with ">", which is what the login waits for. */
	private static final String LOGIN_PROMPT = ">";

	/** Prompt installed by the post connect command; it is the success flag of every command afterwards. */
	private static final String PROMPT = "PXY>";

	private static final int TIMEOUT = 8000;

	private static final int PAGED_TIMEOUT = 20000;

	/** cmd.exe: "prompt PXY$G" makes the prompt exactly "PXY>". */
	private static final String CMD_POST_CONNECT = "prompt PXY$G & echo boot-%COMPUTERNAME%";

	/** powershell.exe: ";" separates statements, $env: is read by the shell itself. */
	private static final String POWERSHELL_POST_CONNECT =
			"function prompt { \"PXY\" + \">\" }; echo boot-$env:COMPUTERNAME";

	@TempDir
	Path tempDir;

	private Connection connection;

	@BeforeEach
	void connectToLocalShell() {
		connection = connect(SHELL_PATH);
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
		assertTrue(connection.getPostConnectOutput().contains("boot-" + COMPUTER_NAME),
				"actual postConnectOutput: " + connection.getPostConnectOutput());
	}

	@Test
	void sendCommand_singleCommand_returnsItsEcho() {
		CommandResult result = connection.sendCommand(command("ver"));

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("Microsoft Windows"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_customSuccessFlag_matchesTheOutputInsteadOfThePrompt() {
		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("ver")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags("Microsoft Windows")
				.build());

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("Microsoft Windows"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_chainedCommands_runInOrder() {
		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("cd /d %SystemRoot%")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.next("cd")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build());

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("C:\\Windows"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_separateCalls_shareTheSameShellSession() {
		assertTrue(connection.sendCommand(command("cd /d %SystemRoot%")).isSuccess());

		CommandResult result = connection.sendCommand(command("cd"));

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("C:\\Windows"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagMatched_returnsFailureWithTheFailingCommand() {
		CommandConfigurer failing = CommandConfigurerBuilder
				.newCommandConfigurer("echo FAILED-%COMPUTERNAME%")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.failFlags("FAILED-" + COMPUTER_NAME)
				.build();

		CommandResult result = connection.sendCommand(failing);

		assertFalse(result.isSuccess());
		assertSame(failing, result.getFailCommand());
		assertTrue(result.getResult().contains("FAILED-" + COMPUTER_NAME), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_failFlagInChain_stopsTheFollowingCommands() {
		CommandConfigurer first = CommandConfigurerBuilder
				.newCommandConfigurer("echo FAILED-%COMPUTERNAME%")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.failFlags("FAILED-" + COMPUTER_NAME)
				.build();
		first.setNext(command("echo should-not-run"));

		CommandResult result = connection.sendCommand(first);

		assertFalse(result.isSuccess());
		assertSame(first, result.getFailCommand());
		assertFalse(result.getResult().contains("should-not-run"), "actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_moreFlag_fetchesEveryPage() throws IOException {
		// more.com writes the page content through the console API and winpty does not forward the screen buffer
		// to the pty - only its "-- More --" status line arrives, so the pages can never be observed on Windows.
		// On a Unix pty (bash + more) the same command exercises the moreFlag/moreCommand mechanism for real.
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"),
				"a real pager cannot be driven through winpty on Windows");

		Path pages = tempDir.resolve("pages.txt");
		List<String> lines = new ArrayList<>();
		for (int i = 1; i <= 120; i++) {
			lines.add("page-" + i);
		}
		Files.write(pages, lines, StandardCharsets.UTF_8);

		CommandResult result = connection.sendCommand(CommandConfigurerBuilder
				.newCommandConfigurer("more < \"" + pages + "\"")
				.enter(ENTER)
				.timeoutMilliSeconds(PAGED_TIMEOUT)
				// The prompt cannot be the success flag here: while more runs, winpty repaints the line and the
				// repaint contains the prompt, which would end the wait long before the last page is shown.
				.successFlags("page-120")
				.moreFlag("-- More")
				.moreCommand(" ")
				.build());

		assertTrue(result.isSuccess(), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("-- More"),
				"the pager prompt should have appeared, actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("page-120"),
				"the last page should have been reached, actual echo: " + result.getResult());
	}

	@Test
	void sendCommand_commandFunction_receivesThePreviousEcho() {
		CommandConfigurer first = CommandConfigurerBuilder
				.newCommandConfigurer("echo token-%COMPUTERNAME%")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build();
		first.setNext(CommandConfigurerBuilder
				.newCommandConfigurer((Function<String, String>) previousEcho ->
						previousEcho.contains("token-" + COMPUTER_NAME)
								? "echo found-%COMPUTERNAME%"
								: "echo not-found")
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build());

		CommandResult result = connection.sendCommand(first);

		assertTrue(result.isSuccess());
		assertTrue(result.getResult().contains("found-" + COMPUTER_NAME), "actual echo: " + result.getResult());
	}

	@Test
	void close_runsThePreDisconnectCommand() {
		Path marker = tempDir.resolve("predisconnect.txt");
		Connection withPreDisconnect = null;
		try {
			withPreDisconnect = new ShellConnectionFactory().createConnection(configurerBuilder(SHELL_PATH)
					.preDisconnect(command("echo marker-%COMPUTERNAME% > \"" + marker + "\""))
					.build());
			assertTrue(withPreDisconnect.isConnected());
		}
		finally {
			if (withPreDisconnect != null) {
				withPreDisconnect.close();
			}
		}

		CommandResult result = connection.sendCommand(command("type \"" + marker + "\""));
		assertTrue(result.isSuccess(), "actual echo: " + result.getResult());
		assertTrue(result.getResult().contains("marker-" + COMPUTER_NAME), "actual echo: " + result.getResult());
	}

	@Test
	void powershell_runsThePostConnectCommand() {
		Connection powershell = connect(POWERSHELL_PATH);
		try {
			assertTrue(powershell.isConnected());
			assertTrue(powershell.getPostConnectOutput().contains("boot-" + COMPUTER_NAME),
					"actual postConnectOutput: " + powershell.getPostConnectOutput());
		}
		finally {
			powershell.close();
		}
	}

	@Test
	void powershell_runsASingleCommand() {
		Connection powershell = connect(POWERSHELL_PATH);
		try {
			CommandResult result = powershell.sendCommand(command("$env:COMPUTERNAME"));

			assertTrue(result.isSuccess());
			assertTrue(result.getResult().contains(COMPUTER_NAME), "actual echo: " + result.getResult());
		}
		finally {
			powershell.close();
		}
	}

	@Test
	void powershell_runsChainedCommands() {
		Connection powershell = connect(POWERSHELL_PATH);
		try {
			CommandResult result = powershell.sendCommand(CommandConfigurerBuilder
					.newCommandConfigurer("cd $env:SystemRoot")
					.enter(ENTER)
					.timeoutMilliSeconds(TIMEOUT)
					.successFlags(PROMPT)
					.next("Get-Location")
					.enter(ENTER)
					.timeoutMilliSeconds(TIMEOUT)
					.successFlags(PROMPT)
					.build());

			assertTrue(result.isSuccess());
			// $env:SystemRoot is upper case, cmd.exe reports the same directory in mixed case
			assertTrue(result.getResult().toLowerCase().contains("c:\\windows"), "actual echo: " + result.getResult());
		}
		finally {
			powershell.close();
		}
	}

	private static Connection connect(String shellPath) {
		try {
			return new ShellConnectionFactory().createConnection(configurerBuilder(shellPath).build());
		}
		catch (RuntimeException e) {
			return Assumptions.abort("cannot start " + shellPath + " (" + e + ")");
		}
	}

	private static ConnectionConfigurerBuilder configurerBuilder(String shellPath) {
		return ConnectionConfigurerBuilder.shell()
				.shellPath(shellPath)
				.charset(CHARSET)
				.successFlags(LOGIN_PROMPT)
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.postConnect(CommandConfigurerBuilder
						.newCommandConfigurer(postConnectCommand(shellPath))
						.enter(ENTER)
						.timeoutMilliSeconds(TIMEOUT)
						.successFlags(PROMPT)
						.build());
	}

	private static String postConnectCommand(String shellPath) {
		return shellPath.toLowerCase().contains("powershell") || shellPath.toLowerCase().contains("pwsh")
				? POWERSHELL_POST_CONNECT
				: CMD_POST_CONNECT;
	}

	private static CommandConfigurer command(String command) {
		return CommandConfigurerBuilder.newCommandConfigurer(command)
				.enter(ENTER)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(PROMPT)
				.build();
	}

}
