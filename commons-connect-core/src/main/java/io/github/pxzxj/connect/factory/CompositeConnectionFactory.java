package io.github.pxzxj.connect.factory;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionFactory;
import io.github.pxzxj.connect.GeneralConnectionException;

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
    public boolean supports(ConnectionConfigurer connectionConfigurer) {
        return delegates.stream().anyMatch(f -> f.supports(connectionConfigurer));
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        return delegates.stream().filter(f -> f.supports(connectionConfigurer))
                                .findFirst().orElse(new TelnetConnectionFactory())
                                .createConnection(connectionConfigurer);
    }
}
