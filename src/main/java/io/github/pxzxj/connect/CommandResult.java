package io.github.pxzxj.connect;

public class CommandResult {

    private final boolean success;

    private final CommandConfigurer failCommand;

    private final String result;

	private CommandResult(boolean success, CommandConfigurer failCommand, String result) {
		this.success = success;
		this.failCommand = failCommand;
		this.result = result;
	}

    public static CommandResult successfulResult(String result){
        return new CommandResult(true, null, result);
    }

    public static CommandResult failResult(String result) {
        return new CommandResult(false,  null, result);
    }

	public static CommandResult failResult(CommandConfigurer failCommand, String result) {
		return new CommandResult(false,  failCommand, result);
	}

	public boolean isSuccess() {
		return success;
	}

	public CommandConfigurer getFailCommand() {
		return failCommand;
	}

	public String getResult() {
		return result;
	}

	@Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", failCommand=" + failCommand +
                ", result='" + result + '\'' +
                '}';
    }
}
