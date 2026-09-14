package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.ConnectionHolder;
import com.ponshine.connection.GeneralConnectionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionFactoryHolder implements ConnectionFactory, ConnectionHolder {

    private final ConnectionFactory delegate;

    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    public ConnectionFactoryHolder(ConnectionFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Collection<Connection> getConnections() {
        return new ArrayList<>(connections);
    }

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return delegate.support(connectionConfigurer);
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        Connection connection = delegate.createConnection(connectionConfigurer);
        connections.removeIf(con -> !con.isConnected());
        connections.add(connection);
        return connection;
    }
}
