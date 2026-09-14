package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.event.ConnectionClosedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class SemaphoreConnectionFactory implements ConnectionFactory, ApplicationListener<ConnectionClosedEvent> {

    private final Map<String, Semaphore> semaphoreMap;

    private final ConnectionFactory delegate;

    private long timeoutMilliSeconds = 0L;

    private Function<ConnectionConfigurer, String> keyConverter = ConnectionConfigurer::getHost;

    public SemaphoreConnectionFactory(Map<String, Semaphore> semaphoreMap, ConnectionFactory delegate) {
        this.semaphoreMap = Collections.unmodifiableMap(semaphoreMap);
        this.delegate = delegate;
    }

    public SemaphoreConnectionFactory(Map<String, Semaphore> semaphoreMap, ConnectionFactory delegate, long timeoutMilliSeconds) {
        this.semaphoreMap = Collections.unmodifiableMap(semaphoreMap);
        this.delegate = delegate;
        this.timeoutMilliSeconds = timeoutMilliSeconds;
    }

    public SemaphoreConnectionFactory(Map<String, Semaphore> semaphoreMap, ConnectionFactory delegate, Function<ConnectionConfigurer, String> keyConverter) {
        this.semaphoreMap = Collections.unmodifiableMap(semaphoreMap);
        this.delegate = delegate;
        this.keyConverter = keyConverter;
    }

    public SemaphoreConnectionFactory(Map<String, Semaphore> semaphoreMap, ConnectionFactory delegate,
                                      long timeoutMilliSeconds, Function<ConnectionConfigurer, String> keyConverter) {
        this.semaphoreMap = Collections.unmodifiableMap(semaphoreMap);
        this.delegate = delegate;
        this.timeoutMilliSeconds = timeoutMilliSeconds;
        this.keyConverter = keyConverter;
    }

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return delegate.support(connectionConfigurer);
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        Assert.hasText(connectionConfigurer.getHost(), "host must hasText!");
        Semaphore semaphore = semaphoreMap.get(keyConverter.apply(connectionConfigurer));
        try {
            if(semaphore != null) {
                if(timeoutMilliSeconds > 0) {
                    boolean acquired = semaphore.tryAcquire(timeoutMilliSeconds, TimeUnit.MILLISECONDS);
                    if(!acquired) {
                        throw new GeneralConnectionException("semaphore acquire timeout! host: " + connectionConfigurer.getHost());
                    }
                } else {
                    semaphore.acquire();
                }
            }
            return delegate.createConnection(connectionConfigurer);
        } catch (Exception e) {
            throw new GeneralConnectionException("", e);
        }
    }

    @Override
    public void onApplicationEvent(ConnectionClosedEvent connectionClosedEvent) {
        Connection connection = (Connection) connectionClosedEvent.getSource();
        Semaphore semaphore = semaphoreMap.get(keyConverter.apply(connection.getConnectionConfigurer()));
        if(semaphore != null) {
            semaphore.release();
        }
    }
}
