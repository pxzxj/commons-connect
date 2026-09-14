package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.TelnetConnection;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import org.apache.commons.net.telnet.TelnetClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class TelnetConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;
    
    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "telnet".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) {
        Assert.notNull(connectionConfigurer.getHost(), "host cannot be null!");
        try {
            TelnetClient telnetClient = new TelnetClient();
            telnetClient.connect(connectionConfigurer.getHost(), connectionConfigurer.getPort());
            String connectionId = "telnet-" + seq.incrementAndGet();
            TelnetConnection connection = new TelnetConnection(connectionId, connectionConfigurer, telnetClient.getInputStream(), telnetClient.getOutputStream(), telnetClient);
            connection.postConnect();
            if(applicationEventPublisher != null){
                connection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(connection));
            }
            return connection;
        } catch (IOException e){
            throw new GeneralConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
