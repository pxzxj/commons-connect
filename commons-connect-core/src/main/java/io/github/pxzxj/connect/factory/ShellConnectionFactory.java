package io.github.pxzxj.connect.factory;

import com.ponshine.connection.Connection;
import com.ponshine.connection.ConnectionConfigurer;
import com.ponshine.connection.GeneralConnectionException;
import com.ponshine.connection.ShellConnection;
import com.ponshine.connection.event.ConnectionCreatedEvent;
import com.ponshine.connection.support.SystemInfoRt;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShellConnectionFactory implements ConnectionFactory, ApplicationEventPublisherAware {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnectionFactory.class);

    private final AtomicInteger seq = new AtomicInteger();

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return "shell".equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        String shellPath = connectionConfigurer.getExtAttrs().get(ConnectionConfigurer.SHELL_PATH_ATTR);
        String[] command;
        if(shellPath == null) {
            command = SystemInfoRt.isWindows ? new String[]{"cmd.exe"} : new String[]{"/bin/bash", "--login"};
            logger.info("no specified shellPath, use {}", Arrays.toString(command));
        } else {
            command = new String[]{shellPath};
        }
        Map<String, String> environment = new HashMap<>(System.getenv());
        if(!SystemInfoRt.isWindows) {
            environment.put("TERM", "vt100");
        }
        try {
            PtyProcess ptyProcess = new PtyProcessBuilder(command).setEnvironment(environment).start();
            String connectionId = "shell-" + seq.incrementAndGet();
            ShellConnection shellConnection = new ShellConnection(connectionId, connectionConfigurer, ptyProcess);
            shellConnection.postConnect();
            if(applicationEventPublisher != null) {
                shellConnection.setApplicationEventPublisher(applicationEventPublisher);
                applicationEventPublisher.publishEvent(new ConnectionCreatedEvent(shellConnection));
            }
            return shellConnection;
        } catch (IOException e) {
            logger.error("", e);
            throw new GeneralConnectionException("create shell connection fail!", e);
        }
    }


    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}