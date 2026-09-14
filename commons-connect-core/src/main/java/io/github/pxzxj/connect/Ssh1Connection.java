package io.github.pxzxj.connect;

import com.mindbright.ssh.SSHConsoleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class Ssh1Connection extends ConnectionBase {

    private final Logger logger = LoggerFactory.getLogger(Ssh1Connection.class);

    private SSHConsoleClient sshConsoleClient;

    public Ssh1Connection(String connectionId, ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream, SSHConsoleClient sshConsoleClient) throws UnsupportedEncodingException {
        super(connectionId, connectionConfigurer, inputStream, outputStream);
        this.sshConsoleClient = sshConsoleClient;
    }

    @Override
    public boolean isConnected() {
        // 没找到对应方法
        return true;
    }

    @Override
    public void close() {
        try {
            logout();
        } catch (Exception e){
            logger.error("logout error ", e);
        }
        try {
            sshConsoleClient.close();
        } catch (Exception e){
            logger.error("close connection error ", e);
        }
    }

}
