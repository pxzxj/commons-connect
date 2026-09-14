package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;

import java.util.Arrays;
import java.util.List;

public class CompositeConnectionFactory implements ConnectionFactory {

    private final List<ConnectionFactory> delegates;

    public CompositeConnectionFactory(List<ConnectionFactory> delegates) {
        this.delegates = delegates;
    }

    public CompositeConnectionFactory(ConnectionFactory... delegates) {
        this.delegates = Arrays.asList(delegates);
    }

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return delegates.stream().anyMatch(f -> f.support(connectionConfigurer));
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        return delegates.stream().filter(f -> f.support(connectionConfigurer))
                                .findFirst().orElse(new TelnetConnectionFactory())
                                .createConnection(connectionConfigurer);
    }
}
