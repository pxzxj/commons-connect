package io.github.pxzxj.connect.factory;

import com.mindbright.jca.security.interfaces.RSAPublicKey;
import com.mindbright.ssh.*;
import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.Ssh1Connection;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class Ssh1ConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "ssh1".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        SSH1Handler ssh1Handler = new SSH1Handler(connectionConfigurer.getHost(), connectionConfigurer.getPort(), connectionConfigurer.getUsername(),
                connectionConfigurer.getPassword());
        SSHConsoleClient sshConsoleClient = null;
        try {
            sshConsoleClient = new SSHConsoleClient(connectionConfigurer.getHost(), connectionConfigurer.getPort(), ssh1Handler, ssh1Handler);
            boolean success = sshConsoleClient.shell();
            Assert.isTrue(success, "ssh1Client shell fail!");
            Ssh1Connection ssh1Connection = new Ssh1Connection("ssh1" + seq.incrementAndGet(), connectionConfigurer, sshConsoleClient.getStdOut(), sshConsoleClient.getStdIn(), sshConsoleClient);
            ssh1Connection.postConnect();
            if(applicationEventPublisher != null){
                ssh1Connection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(ssh1Connection));
            }
            return ssh1Connection;
        } catch (Exception e) {
            throw new GeneralConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    static class SSH1Handler extends SSHInteractorAdapter implements SSHAuthenticator, SSHClientUser {

        private String server;
        private int port;
        private String username;
        private String password;
        private final String AUTH_TYPE = "password";

        public SSH1Handler(String server, int port, String username, String password) {
            this.server = server;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        public int getAliveInterval() {
            return 0;
        }

        public int getCompressionLevel() {
            return 0;
        }

        public String getDisplay() {
            return null;
        }

        public SSHInteractor getInteractor() {
            return this;
        }

        public int getMaxPacketSz() {
            return 0;
        }

        public Socket getProxyConnection() throws IOException {
            return null;
        }

        public String getSrvHost() throws IOException {
            return server;
        }

        public int getSrvPort() {
            return port;
        }

        public boolean wantPTY() {
            return false;
        }

        public boolean wantX11Forward() {
            return false;
        }

        public int[] getAuthTypes(SSHClientUser arg0) {
            return SSH.getAuthTypes(AUTH_TYPE);
        }

        public String getChallengeResponse(SSHClientUser arg0, String arg1)
                throws IOException {
            return null;
        }

        public int getCipher(SSHClientUser arg0) {
            return SSH.CIPHER_3DES;
        }

        public SSHRSAKeyFile getIdentityFile(SSHClientUser arg0) throws IOException {
            String idfile = System.getProperty("user.home") + File.separatorChar + ".ssh" + File.separatorChar + "identity";
            return new SSHRSAKeyFile(idfile);
        }

        public String getIdentityPassword(SSHClientUser arg0) throws IOException {
            return null;
        }

        public String getPassword(SSHClientUser arg0) throws IOException {
            return password;
        }

        public String getUsername(SSHClientUser arg0) throws IOException {
            return username;
        }

        public boolean verifyKnownHosts(RSAPublicKey arg0) throws IOException {
            return true;
        }
    }

}
