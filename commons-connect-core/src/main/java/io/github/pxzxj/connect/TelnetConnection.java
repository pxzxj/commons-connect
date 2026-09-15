package io.github.pxzxj.connect;

import org.apache.commons.net.telnet.TelnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class TelnetConnection extends ConnectionBase {

    private final Logger logger = LoggerFactory.getLogger(TelnetConnection.class);

    private final TelnetClient telnetClient;

    public TelnetConnection(String connectionId, ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream, TelnetClient telnetClient) throws UnsupportedEncodingException {
        super(connectionId, connectionConfigurer, inputStream, outputStream);
        this.telnetClient = telnetClient;
    }

	public TelnetClient getTelnetClient() {
		return telnetClient;
	}

	@Override
    public boolean isConnected() {
        return telnetClient.isConnected();
    }

    @Override
    public synchronized void close() {
        try {
            preDisconnect();
        } catch (Exception e) {
            logger.error("logout error ", e);
        }
        try {
            telnetClient.disconnect();
        } catch (Exception e) {
            logger.error("close connection error ", e);
        }
    }
}
