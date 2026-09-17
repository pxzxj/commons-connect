package io.github.pxzxj.connect.factory;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.github.pxzxj.connect.*;
import io.github.pxzxj.connect.support.JschLogger;
import io.github.pxzxj.connect.support.JschUserInfo;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;

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
            String connectionId = StringUtils.isNotBlank(connectionConfigurer.getId()) ? connectionConfigurer.getId() : connectionConfigurer.getHost();
            SshConnection connection = new SshConnection(connectionId, session, channelShell, connectionConfigurer, channelShell.getInputStream(), channelShell.getOutputStream());
            connection.postConnect();
            return connection;
        } catch (IOException | JSchException e) {
            throw new GeneralConnectionException(e.getMessage(), e);
        }
    }

}
