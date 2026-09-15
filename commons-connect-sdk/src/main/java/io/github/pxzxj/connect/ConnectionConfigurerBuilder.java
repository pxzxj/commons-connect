package io.github.pxzxj.connect;

import java.util.HashMap;
import java.util.Map;

public class ConnectionConfigurerBuilder {

    private String id;
    private String type;
    private String host;
    private int port;
    private String shellPath;
    private String charset;
    private String username;
    private String password;
    private CommandConfigurer postConnect;
    private CommandConfigurer preDisconnect;
    private String[] successFlags;
    private String[] failFlags;
    private int timeoutMilliSeconds = 5000;
    private int keepAliveInterval = 60000;
    private String keepAliveCommand;
    private String keepAliveWaitStr;
    private int keepAliveWaitTimeout;
    private Map<String, Object> extAttrs = new HashMap<>();

    private ConnectionConfigurerBuilder(){}

    public static ConnectionConfigurerBuilder telnet() {
        ConnectionConfigurerBuilder connectionConfigurerBuilder = new ConnectionConfigurerBuilder();
        connectionConfigurerBuilder.type = ConnectionConfigurer.TYPE_TELNET;
        return connectionConfigurerBuilder;
    }

    public static ConnectionConfigurerBuilder ssh() {
        ConnectionConfigurerBuilder connectionConfigurerBuilder = new ConnectionConfigurerBuilder();
        connectionConfigurerBuilder.type = ConnectionConfigurer.TYPE_SSH;
        connectionConfigurerBuilder.port = 22;
        return connectionConfigurerBuilder;
    }

    public static ConnectionConfigurerBuilder shell() {
        ConnectionConfigurerBuilder connectionConfigurerBuilder = new ConnectionConfigurerBuilder();
        connectionConfigurerBuilder.type = ConnectionConfigurer.TYPE_SHELL;
        return connectionConfigurerBuilder;
    }

    public static ConnectionConfigurerBuilder type(String type) {
        ConnectionConfigurerBuilder connectionConfigurerBuilder = new ConnectionConfigurerBuilder();
        connectionConfigurerBuilder.type = type;
        return connectionConfigurerBuilder;
    }

    public ConnectionConfigurerBuilder id(String id) {
        this.id = id;
        return this;
    }

    public ConnectionConfigurerBuilder host(String host) {
        this.host = host;
        return this;
    }

    public ConnectionConfigurerBuilder port(int port){
        this.port = port;
        return this;
    }

    public ConnectionConfigurerBuilder shellPath(String shellPath) {
        this.shellPath = shellPath;
        return this;
    }

    public ConnectionConfigurerBuilder charset(String charset){
        this.charset = charset;
        return this;
    }

    public ConnectionConfigurerBuilder username(String username){
        this.username = username;
        return this;
    }

    public ConnectionConfigurerBuilder password(String password){
        this.password = password;
        return this;
    }

    public ConnectionConfigurerBuilder postConnect(CommandConfigurer postConnect){
        this.postConnect = postConnect;
        return this;
    }

    public ConnectionConfigurerBuilder preLogout(CommandConfigurer preLogout){
        this.preDisconnect = preLogout;
        return this;
    }

    public ConnectionConfigurerBuilder successFlags(String... successFlags){
        this.successFlags = successFlags;
        return this;
    }

    public ConnectionConfigurerBuilder failFlags(String... failFlags){
        this.failFlags = failFlags;
        return this;
    }

    public ConnectionConfigurerBuilder timeoutMilliSeconds(int timeoutMilliSeconds){
        this.timeoutMilliSeconds = timeoutMilliSeconds;
        return this;
    }

    public ConnectionConfigurerBuilder keepAliveInterval(int keepAliveInterval){
        this.keepAliveInterval = keepAliveInterval;
        return this;
    }

    public ConnectionConfigurerBuilder keepAliveCommand(String keepAliveCommand){
        this.keepAliveCommand = keepAliveCommand;
        return this;
    }

    public ConnectionConfigurerBuilder keepAliveWaitStr(String keepAliveWaitStr){
        this.keepAliveWaitStr = keepAliveWaitStr;
        return this;
    }

    public ConnectionConfigurerBuilder keepAliveWaitTimeout(int keepAliveWaitTimeout){
        this.keepAliveWaitTimeout = keepAliveWaitTimeout;
        return this;
    }

    public ConnectionConfigurerBuilder extAttrs(Map<String, Object> extAttrs){
        this.extAttrs = extAttrs;
        return this;
    }

    public ConnectionConfigurer build(){
        ConnectionConfigurer connectionConfigurer = new ConnectionConfigurer();
        connectionConfigurer.setId(id);
        connectionConfigurer.setType(type);
        connectionConfigurer.setHost(host);
        if(port != 0){
            connectionConfigurer.setPort(port);
        }
        connectionConfigurer.setShellPath(shellPath);
        if(charset != null){
            connectionConfigurer.setCharset(charset);
        }
        connectionConfigurer.setUsername(username);
        connectionConfigurer.setPassword(password);
        connectionConfigurer.setPostConnect(postConnect);
        connectionConfigurer.setPreDisconnect(preDisconnect);
        connectionConfigurer.setSuccessFlags(successFlags);
        connectionConfigurer.setFailFlags(failFlags);
        if(timeoutMilliSeconds != 0){
            connectionConfigurer.setTimeoutMilliSeconds(timeoutMilliSeconds);
        }
        if(keepAliveInterval != 0){
            connectionConfigurer.setKeepAliveInterval(keepAliveInterval);
        }
        if(keepAliveCommand != null){
            connectionConfigurer.setKeepAliveCommand(keepAliveCommand);
        }
        connectionConfigurer.setKeepAliveWaitStr(keepAliveWaitStr);
        if(keepAliveWaitTimeout != 0){
            connectionConfigurer.setKeepAliveWaitTimeout(keepAliveWaitTimeout);
        }
        connectionConfigurer.setExtAttrs(extAttrs);
        return connectionConfigurer;
    }


}
