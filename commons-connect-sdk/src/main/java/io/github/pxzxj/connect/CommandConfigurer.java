package io.github.pxzxj.connect;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.Function;

public class CommandConfigurer implements Serializable {

    private static final int DEFAULT_WAIT_TIMEOUT = 5000;

    public static final String BACKSLASH_N = "\n";
    public static final String BACKSLASH_RN = "\r\n";

    private String command;
    /**
     * 生成指令的函数，输入为前面所有指令执行的回显
     */
    private Function<String, String> commandFunction;

    /**
     * 超时时间
     */
    private int timeoutMilliSeconds = DEFAULT_WAIT_TIMEOUT;
    /**
     * 成功标识，成功标识非空时会等待成功标识，
     * 否则尝试等待失败标识，若失败标识也为空则等默认标识
     * 默认标识会使用一定不会出现的字符串
     */
    private String[] successFlags;
    /**
     * 失败标识
     */
    private String[] failFlags;
    /**
     * more标识
     */
    private String moreFlag;
    /**
     * 获取下一屏内容需要执行的命令，注意下发该命令时不会自动添加换行符
     */
    private String moreCommand = " ";
    /**
     * 回车符
     */
    private String enter = BACKSLASH_N;
    /**
     * 下一条指令，如果当前指令的成功标识或失败标识非空则会在当前指令成功后才
     * 执行下一条指令，否则不判断当前指令是否执行成功
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
