package io.github.pxzxj.connect.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.pxzxj.connect.ConnectionConfigurer;

public class SshConnection extends ConnectionBase {

    private final static Logger logger = LoggerFactory.getLogger(SshConnection.class);

    private final Session session;
    private final ChannelShell channelShell;

    public SshConnection(Session session, ChannelShell channelShell,
                         ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream) throws UnsupportedEncodingException {
        super(connectionConfigurer, inputStream, outputStream);
        this.session = session;
        this.channelShell = channelShell;
    }

	public Session getSession() {
		return session;
	}

	public ChannelShell getChannelShell() {
		return channelShell;
	}

	@Override
    public boolean isConnected() {
        return channelShell.isConnected();
    }

    @Override
    public synchronized void close() {
        try {
            preDisconnect();
        } catch (Exception e){
            logger.error("host: {}, logout error", host, e);
        }
        try {
            session.disconnect();
        } catch (Exception e){
            logger.error("host: {}, close connection error", host, e);
        }
    }

}
