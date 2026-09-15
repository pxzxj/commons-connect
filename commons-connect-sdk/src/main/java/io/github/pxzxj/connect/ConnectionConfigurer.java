package io.github.pxzxj.connect;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ConnectionConfigurer {

    private static final String DEFAULT_KEEP_ALIVE_COMMAND = " ";

    public static final String TYPE_SSH = "ssh";

    public static final String TYPE_TELNET = "telnet";

    public static final String TYPE_SHELL = "shell";

    private String id;

    private String type;
    
    private String host;
    
    private int port = 23;
    
    private String shellPath;
    
    private String charset = "utf-8";
    /**
     * 登录用户名
     */
    private String username;
    /**
     * 登录密码
     */
    private String password;
    /**
     * 连接成功后发送的命令
     */
    private CommandConfigurer postConnect;
    /**
     * 登出前发送的命令
     */
    private CommandConfigurer preDisconnect;
    /**
     * socket连接后的成功标识
     */
    private String[] successFlags;
    /**
     * socket连接后的失败标识
     */
    private String[] failFlags;
    /**
     * 等待成功或失败标识的时间
     */
    private int timeoutMilliSeconds = 5000;
    /**
     * 保持连接活动时间间隔
     */
    private int keepAliveInterval = 60000;
    /**
     * 保持连接活动指令
     */
    private String keepAliveCommand = DEFAULT_KEEP_ALIVE_COMMAND;
    /**
     * 保活命令等待回显
     */
    private String keepAliveWaitStr = CommandConfigurer.DEFAULT_WAIT_STR;
    /**
     * 保活命令登到回显时长
     */
    private int keepAliveWaitTimeout = 2000;

    private Map<String, Object> extAttrs = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getShellPath() {
        return shellPath;
    }

    public void setShellPath(String shellPath) {
        this.shellPath = shellPath;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public CommandConfigurer getPostConnect() {
        return postConnect;
    }

    public void setPostConnect(CommandConfigurer postConnect) {
        this.postConnect = postConnect;
    }

    public CommandConfigurer getPreDisconnect() {
        return preDisconnect;
    }

    public void setPreDisconnect(CommandConfigurer preDisconnect) {
        this.preDisconnect = preDisconnect;
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

    public int getTimeoutMilliSeconds() {
        return timeoutMilliSeconds;
    }

    public void setTimeoutMilliSeconds(int timeoutMilliSeconds) {
        this.timeoutMilliSeconds = timeoutMilliSeconds;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public String getKeepAliveCommand() {
        return keepAliveCommand;
    }

    public void setKeepAliveCommand(String keepAliveCommand) {
        this.keepAliveCommand = keepAliveCommand;
    }

    public String getKeepAliveWaitStr() {
        return keepAliveWaitStr;
    }

    public void setKeepAliveWaitStr(String keepAliveWaitStr) {
        this.keepAliveWaitStr = keepAliveWaitStr;
    }

    public int getKeepAliveWaitTimeout() {
        return keepAliveWaitTimeout;
    }

    public void setKeepAliveWaitTimeout(int keepAliveWaitTimeout) {
        this.keepAliveWaitTimeout = keepAliveWaitTimeout;
    }

    public Map<String, Object> getExtAttrs() {
        return extAttrs;
    }

    public void setExtAttrs(Map<String, Object> extAttrs) {
        this.extAttrs = extAttrs;
    }

    @Override
    public String toString() {
        return "ConnectionConfigurer{" +
                "type='" + type + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", shellPath='" + shellPath + '\'' +
                ", charset='" + charset + '\'' +
                ", username='" + username + '\'' +
                ", postConnect=" + postConnect +
                ", preDisconnect=" + preDisconnect +
                ", successFlags=" + Arrays.toString(successFlags) +
                ", failFlags=" + Arrays.toString(failFlags) +
                ", timeoutMilliSeconds=" + timeoutMilliSeconds +
                ", keepAliveInterval=" + keepAliveInterval +
                ", keepAliveCommand='" + keepAliveCommand + '\'' +
                ", keepAliveWaitStr='" + keepAliveWaitStr + '\'' +
                ", keepAliveWaitTimeout=" + keepAliveWaitTimeout +
                ", extAttrs=" + extAttrs +
                '}';
    }
}
