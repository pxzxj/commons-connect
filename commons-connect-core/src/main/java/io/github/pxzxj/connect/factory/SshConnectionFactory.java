package io.github.pxzxj.connect.factory;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.SshConnection;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import com.ponshine.connection.support.JschLogger;
import com.ponshine.connection.support.JschUserInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class SshConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;

    static {
        JSch.setLogger(new JschLogger());
    }

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "ssh".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) {
        Assert.notNull(connectionConfigurer.getUsername(), "username cannot be null!");
        Assert.notNull(connectionConfigurer.getHost(), "host cannot be null!");
        Assert.notNull(connectionConfigurer.getPassword(), "password cannot be null!");
        try {
            JSch jSch = new JSch();
            Session session = jSch.getSession(connectionConfigurer.getUsername(), connectionConfigurer.getHost(), connectionConfigurer.getPort());
            session.setUserInfo(new JschUserInfo(connectionConfigurer.getPassword()));
            session.connect();
            ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
            channelShell.setPtyType("vt100", 320, 32, 1024, 768);
            channelShell.connect();
            String connectionId = "ssh-" + seq.incrementAndGet();
            SshConnection connection = new SshConnection(connectionId, session, channelShell, connectionConfigurer, channelShell.getInputStream(), channelShell.getOutputStream());
            connection.postConnect();
            if(applicationEventPublisher != null){
                connection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(connection));
            }
            return connection;
        } catch (IOException | JSchException e) {
            throw new GeneralConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
