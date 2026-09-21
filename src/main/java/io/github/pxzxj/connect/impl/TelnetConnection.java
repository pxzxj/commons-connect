package io.github.pxzxj.connect.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.commons.net.telnet.TelnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.pxzxj.connect.ConnectionConfigurer;

public class TelnetConnection extends ConnectionBase {

    private final Logger logger = LoggerFactory.getLogger(TelnetConnection.class);

    private final TelnetClient telnetClient;

    public TelnetConnection(ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream, TelnetClient telnetClient) throws UnsupportedEncodingException {
        super(connectionConfigurer, inputStream, outputStream);
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
            logger.error("host: {}, logout error", host, e);
        }
        try {
            telnetClient.disconnect();
        } catch (Exception e) {
            logger.error("host: {}, close connection error", host, e);
        }
    }
}
