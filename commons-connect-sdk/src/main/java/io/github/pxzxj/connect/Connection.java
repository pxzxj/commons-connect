package io.github.pxzxj.connect;

public interface Connection extends AutoCloseable {

    String getConnectionId();

    long getCreateTime();

    /**
     * 连接后回显，例如ssh motd
     * @return
     */
    String getPostConnectOutput();

    ConnectionConfigurer getConnectionConfigurer();

    /**
     * 发送命令并获取回显
     * @param commandConfigurer 命令配置
     * @return 执行结果
     */
    CommandResult sendCommand(CommandConfigurer commandConfigurer);

    /**
     * 连接是否正常
     * @return
     */
    boolean isConnected();

    /**
     * 关闭连接
     */
    void close();

}
