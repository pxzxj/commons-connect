package io.github.pxzxj.connect;

public interface Connection extends AutoCloseable {

    String getConnectionId();

    long getCreateTime();

    /**
     * Echo received after connecting, e.g. the ssh motd
     * @return
     */
    String getPostConnectOutput();

    ConnectionConfigurer getConnectionConfigurer();

    /**
     * Sends a command and returns its echo
     * @param commandConfigurer command configuration
     * @return command execution result
     */
    CommandResult sendCommand(CommandConfigurer commandConfigurer);

    /**
     * Whether the connection is still alive
     * @return
     */
    boolean isConnected();

    /**
     * Closes the connection
     */
    void close();

}
