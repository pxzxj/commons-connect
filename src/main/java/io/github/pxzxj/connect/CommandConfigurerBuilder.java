package io.github.pxzxj.connect;

import java.util.function.Function;

public class CommandConfigurerBuilder {

    private String command;
    private Function<String, String> commandFunction;
    private int timeoutMilliSeconds;
    private String[] successFlags;
    private String[] failFlags;
    private String moreFlag;
    private String moreCommand;
    private String enter;
    private CommandConfigurerBuilder original;

    public static CommandConfigurerBuilder newCommandConfigurer(String command) {
        CommandConfigurerBuilder builder = new CommandConfigurerBuilder();
        builder.command = command;
        return builder;
    }

    public static CommandConfigurerBuilder newCommandConfigurer(Function<String, String> commandFunction) {
        CommandConfigurerBuilder builder = new CommandConfigurerBuilder();
        builder.commandFunction = commandFunction;
        return builder;
    }

    public CommandConfigurerBuilder timeoutMilliSeconds(int timeoutMilliSeconds) {
        this.timeoutMilliSeconds = timeoutMilliSeconds;
        return this;
    }

    public CommandConfigurerBuilder successFlags(String... successFlags) {
        this.successFlags = successFlags;
        return this;
    }

    public CommandConfigurerBuilder failFlags(String... failFlags) {
        this.failFlags = failFlags;
        return this;
    }

    public CommandConfigurerBuilder moreFlag(String moreFlag) {
        this.moreFlag = moreFlag;
        return this;
    }

    public CommandConfigurerBuilder moreCommand(String moreCommand) {
        this.moreCommand = moreCommand;
        return this;
    }

    public CommandConfigurerBuilder enter(String enter) {
        this.enter = enter;
        return this;
    }

    public CommandConfigurerBuilder next(String command) {
        CommandConfigurerBuilder next = new CommandConfigurerBuilder();
        next.command = command;
        next.original = this;
        return next;
    }

    public CommandConfigurer build() {
        CommandConfigurer currentConfig = null;
        CommandConfigurerBuilder currentBuilder = this;
        while (true) {
            CommandConfigurer nextConfig = currentConfig;
            currentConfig = new CommandConfigurer();
            currentConfig.setNext(nextConfig);
            currentConfig.setCommand(currentBuilder.command);
            currentConfig.setCommandFunction(currentBuilder.commandFunction);
            if (currentBuilder.timeoutMilliSeconds != 0) {
                currentConfig.setTimeoutMilliSeconds(currentBuilder.timeoutMilliSeconds);
            }
            currentConfig.setSuccessFlags(currentBuilder.successFlags);
            currentConfig.setFailFlags(currentBuilder.failFlags);
            currentConfig.setMoreFlag(currentBuilder.moreFlag);
            if (currentBuilder.moreCommand != null) {
                currentConfig.setMoreCommand(currentBuilder.moreCommand);
            }
            if (currentBuilder.enter != null) {
                currentConfig.setEnter(currentBuilder.enter);
            }
            if (currentBuilder.original != null) {
                currentBuilder = currentBuilder.original;
            } else {
                break;
            }
        }
        return currentConfig;
    }

}
