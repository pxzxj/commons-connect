package io.github.pxzxj.connect.factory.impl;

import java.io.IOException;
import java.util.Objects;

import io.github.pxzxj.connect.factory.ConnectionFactory;

import org.apache.commons.net.telnet.TelnetClient;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.GeneralConnectionException;
import io.github.pxzxj.connect.impl.TelnetConnection;

public class TelnetConnectionFactory implements ConnectionFactory {

    @Override
    public boolean supports(ConnectionConfigurer connectionConfigurer) {
        return ConnectionConfigurer.TYPE_TELNET.equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) {
        Objects.requireNonNull(connectionConfigurer.getHost(), "host cannot be null!");
        try {
            TelnetClient telnetClient = new TelnetClient();
            telnetClient.connect(connectionConfigurer.getHost(), connectionConfigurer.getPort());
            TelnetConnection connection = new TelnetConnection(connectionConfigurer, telnetClient.getInputStream(), telnetClient.getOutputStream(), telnetClient);
            connection.postConnect();
            return connection;
        } catch (IOException e){
            throw new GeneralConnectionException("create telnet connection error, host: " + connectionConfigurer.getHost(), e);
        }
    }

}
