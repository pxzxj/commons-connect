package io.github.pxzxj.connect;

/**
 * Common exception for command execution failures
 */
public class GeneralCommandException extends RuntimeException {

    private CommandResult commandResult;

    public GeneralCommandException(String msg) {
        super(msg);
    }

    public GeneralCommandException(String msg, CommandResult commandResult) {
        super(msg);
        this.commandResult = commandResult;
    }

    public GeneralCommandException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public GeneralCommandException(String msg, Throwable cause, CommandResult commandResult) {
        super(msg, cause);
        this.commandResult = commandResult;
    }

    public CommandResult getCommandResult() {
        return commandResult;
    }

}
