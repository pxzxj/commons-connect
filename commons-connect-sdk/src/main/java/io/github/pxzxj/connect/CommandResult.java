package io.github.pxzxj.connect;

public class CommandResult {

    private boolean success = true;
    private CommandConfigurer command;
    private CommandConfigurer failCommand;
    private String result;

    public CommandResult() {
    }

    public CommandResult(boolean success, CommandConfigurer command, CommandConfigurer failCommand, String result) {
        this.success = success;
        this.command = command;
        this.failCommand = failCommand;
        this.result = result;
    }

    public CommandResult(boolean success, CommandConfigurer command, String result) {
        this.success = success;
        this.command = command;
        this.result = result;
    }

    public CommandResult(CommandConfigurer command) {
        this.command = command;
    }

    public CommandResult(boolean success) {
        this.success = success;
    }

    public static CommandResult successfulResult(){
        CommandResult commandResult = new CommandResult();
        commandResult.setSuccess(true);
        return commandResult;
    }

    public static CommandResult successfulResult(String result){
        CommandResult commandResult = new CommandResult();
        commandResult.setSuccess(true);
        commandResult.setResult(result);
        return commandResult;
    }

    public static CommandResult failedResult(String result) {
        CommandResult commandResult = new CommandResult();
        commandResult.setSuccess(false);
        commandResult.setResult(result);
        return commandResult;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public CommandConfigurer getCommand() {
        return command;
    }

    public void setCommand(CommandConfigurer command) {
        this.command = command;
    }

    public CommandConfigurer getFailCommand() {
        return failCommand;
    }

    public void setFailCommand(CommandConfigurer failCommand) {
        this.failCommand = failCommand;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", command=" + command +
                ", failCommand=" + failCommand +
                ", result='" + result + '\'' +
                '}';
    }
}
