package io.github.pxzxj.connect.factory;

import com.jcraft.jsch.*;
import com.ponshine.connection.*;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import com.ponshine.connection.support.JschLogger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class SftpConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;

    static {
        JSch.setLogger(new JschLogger());
    }

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "sftp".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) {
        Assert.notNull(connectionConfigurer.getUsername(), "username cannot be null!");
        Assert.notNull(connectionConfigurer.getHost(), "host cannot be null!");
        Assert.notNull(connectionConfigurer.getPassword(), "password cannot be null!");
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(connectionConfigurer.getUsername(), connectionConfigurer.getHost(), connectionConfigurer.getPort());
            session.setPassword(connectionConfigurer.getPassword());
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            Map<String, String> extAttrs = connectionConfigurer.getExtAttrs();
            if(extAttrs != null && extAttrs.containsKey(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR)) {
                File localPath = new File(extAttrs.get(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR));
                if(!localPath.exists()) {
                    localPath.mkdirs();
                }
                channelSftp.lcd(extAttrs.get(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR));

            }
            String connectionId = "sftp-" + seq.incrementAndGet();
            SftpConnection connection = new SftpConnection(session, channelSftp, connectionId, connectionConfigurer);
            connection.postConnect();
            if(applicationEventPublisher != null){
                connection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(connection));
            }
            return connection;
        } catch (JSchException | SftpException e) {
            throw new GeneralConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
