package io.github.pxzxj.connect.factory;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.GeneralConnectionException;

public interface ConnectionFactory {

    /**
     * Checks whether this factory supports the given connection configuration
     * @param connectionConfigurer connection configuration
     * @return
     */
    boolean supports(ConnectionConfigurer connectionConfigurer);

    /**
     *
     * @param connectionConfigurer connection configuration
     * @return the created connection
     * @throws GeneralConnectionException when the connection could not be created
     */
    Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException;

}
