package io.github.pxzxj.connect;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class SshConnection extends ConnectionBase {

    private final static Logger logger = LoggerFactory.getLogger(SshConnection.class);

    private final Session session;
    private final ChannelShell channelShell;

    public SshConnection(String connectionId, Session session, ChannelShell channelShell,
                         ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream) throws UnsupportedEncodingException {
        super(connectionId, connectionConfigurer, inputStream, outputStream);
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
            logger.error("logout error ", e);
        }
        try {
            session.disconnect();
        } catch (Exception e){
            logger.error("close connection error ", e);
        }
    }

}
