package io.github.pxzxj.connect.factory.impl;

import java.io.IOException;
import java.util.Objects;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionFactory;
import io.github.pxzxj.connect.GeneralConnectionException;
import io.github.pxzxj.connect.impl.SshConnection;
import io.github.pxzxj.connect.support.JschLogger;
import io.github.pxzxj.connect.support.JschUserInfo;

public class SshConnectionFactory implements ConnectionFactory {

    static {
        JSch.setLogger(new JschLogger());
    }

    @Override
    public boolean supports(ConnectionConfigurer connectionConfigurer) {
        return ConnectionConfigurer.TYPE_SSH.equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) {
        Objects.requireNonNull(connectionConfigurer.getUsername(), "username cannot be null!");
        Objects.requireNonNull(connectionConfigurer.getHost(), "host cannot be null!");
        Objects.requireNonNull(connectionConfigurer.getPassword(), "password cannot be null!");
        try {
            JSch jSch = new JSch();
            Session session = jSch.getSession(connectionConfigurer.getUsername(), connectionConfigurer.getHost(), connectionConfigurer.getPort());
            session.setUserInfo(new JschUserInfo(connectionConfigurer.getPassword()));
            session.connect();
            ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
            channelShell.setPtyType("vt100", 320, 32, 1024, 768);
            channelShell.connect();
            SshConnection connection = new SshConnection(session, channelShell, connectionConfigurer, channelShell.getInputStream(), channelShell.getOutputStream());
            connection.postConnect();
            return connection;
        } catch (IOException | JSchException e) {
            throw new GeneralConnectionException("create ssh connection error, host: " + connectionConfigurer.getHost(), e);
        }
    }

}
