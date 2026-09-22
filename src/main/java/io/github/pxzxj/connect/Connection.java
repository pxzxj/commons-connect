package io.github.pxzxj.connect;

public interface Connection extends AutoCloseable {

    /**
     * Echo received after connecting, e.g. the ssh motd
     * @return postConnectOutput
     */
    String getPostConnectOutput();

    /**
     * Sends a command and returns its echo
     * @param commandConfigurer command configuration
     * @return command execution result
     */
    CommandResult sendCommand(CommandConfigurer commandConfigurer);

    /**
     * Whether the connection is still alive
     * @return connected or not
     */
    boolean isConnected();

    /**
     * Closes the connection
     */
    void close();

}
