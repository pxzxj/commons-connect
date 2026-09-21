package io.github.pxzxj.connect;

import java.util.Arrays;
import java.util.function.Function;

public class CommandConfigurer {

    private static final int DEFAULT_WAIT_TIMEOUT = 5000;

	public final static String DEFAULT_WAIT_STR = "(!@#$%)";

    public static final String BACKSLASH_N = "\n";
    public static final String BACKSLASH_RN = "\r\n";

    private String command;
    /**
     * Function that builds the command from the echo of all previous commands
     */
    private Function<String, String> commandFunction;

    /**
     * Timeout in milliseconds
     */
    private int timeoutMilliSeconds = DEFAULT_WAIT_TIMEOUT;
    /**
     * Success flags. When they are set, the connection waits for them,
     * otherwise it waits for the fail flags, and if those are unset too it waits for the default flag.
     * The default flag is a string that never appears in the output.
     */
    private String[] successFlags;
    /**
     * Fail flags
     */
    private String[] failFlags;
    /**
     * Flag that indicates more output is available
     */
    private String moreFlag;
    /**
     * Command sent to fetch the next screen of output, no newline is appended to it
     */
    private String moreCommand = " ";
    /**
     * Enter key
     */
    private String enter = BACKSLASH_N;
    /**
     * Next command. When the current command has success or fail flags, the next
     * command runs only after the current one succeeded, otherwise its result is not checked
     */
    private CommandConfigurer next;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Function<String, String> getCommandFunction() {
        return commandFunction;
    }

    public void setCommandFunction(Function<String, String> commandFunction) {
        this.commandFunction = commandFunction;
    }

    public int getTimeoutMilliSeconds() {
        return timeoutMilliSeconds;
    }

    public void setTimeoutMilliSeconds(int timeoutMilliSeconds) {
        this.timeoutMilliSeconds = timeoutMilliSeconds;
    }

    public String[] getSuccessFlags() {
        return successFlags;
    }

    public void setSuccessFlags(String[] successFlags) {
        this.successFlags = successFlags;
    }

    public String[] getFailFlags() {
        return failFlags;
    }

    public void setFailFlags(String[] failFlags) {
        this.failFlags = failFlags;
    }

    public String getMoreFlag() {
        return moreFlag;
    }

    public void setMoreFlag(String moreFlag) {
        this.moreFlag = moreFlag;
    }

    public String getMoreCommand() {
        return moreCommand;
    }

    public void setMoreCommand(String moreCommand) {
        this.moreCommand = moreCommand;
    }

    public String getEnter() {
        return enter;
    }

    public void setEnter(String enter) {
        this.enter = enter;
    }

    public CommandConfigurer getNext() {
        return next;
    }

    public void setNext(CommandConfigurer next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return "CommandConfigurer{" +
                "command='" + command + '\'' +
                ", commandFunction=" + commandFunction +
                ", timeoutMilliSeconds=" + timeoutMilliSeconds +
                ", successFlags=" + Arrays.toString(successFlags) +
                ", failFlags=" + Arrays.toString(failFlags) +
                ", moreFlag='" + moreFlag + '\'' +
                ", moreCommand='" + moreCommand + '\'' +
                ", enter='" + enter + '\'' +
                ", next=" + next +
                '}';
    }
}
