package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.FtpConnection;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FtpConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private final Logger logger = LoggerFactory.getLogger(FtpConnectionFactory.class);

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "ftp".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        final FTPClient ftp;
        Map<String, String> extAttrs = connectionConfigurer.getExtAttrs();
        if(ConnectionConfigurer.SSL_ENABLE.equals(extAttrs.get(ConnectionConfigurer.SSL_ATTR))) {
            ftp = new FTPSClient();
        } else {
            ftp = new FTPClient();
        }
        try {
            ftp.connect(connectionConfigurer.getHost(), connectionConfigurer.getPort());
            // After connection attempt, you should check the reply code to verify
            // success.
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new GeneralConnectionException("connect ftpConnection fail! not positive completion response");
            }
            if (!ftp.login(connectionConfigurer.getUsername(), connectionConfigurer.getPassword())) {
                ftp.logout();
                throw new GeneralConnectionException("login ftpConnection fail!");
            }
            if(extAttrs.containsKey(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR)) {
                File localPath = new File(extAttrs.get(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR));
                if(!localPath.exists()) {
                    localPath.mkdirs();
                }
            }
            FtpConnection ftpConnection =  new FtpConnection("ftp-" + seq.incrementAndGet(), ftp, connectionConfigurer);
            ftpConnection.postConnect();
            if(applicationEventPublisher != null) {
                ftpConnection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(ftpConnection));
            }
            return ftpConnection;
        } catch (IOException e) {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (final IOException f) {
                    // do nothing
                }
            }
            logger.error("create ftpConnection fail!", e);
            throw new GeneralConnectionException("", e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
