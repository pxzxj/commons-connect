package io.github.pxzxj.connect.factory.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import io.github.pxzxj.connect.factory.ConnectionFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.GeneralConnectionException;
import io.github.pxzxj.connect.impl.ShellConnection;
import io.github.pxzxj.connect.support.SystemInfoRt;

public class ShellConnectionFactory implements ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnectionFactory.class);

    @Override
    public boolean supports(ConnectionConfigurer connectionConfigurer) {
        return ConnectionConfigurer.TYPE_SHELL.equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        String shellPath = connectionConfigurer.getShellPath();
        String[] command;
        if(StringUtils.isEmpty(shellPath)) {
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
            ShellConnection shellConnection = new ShellConnection(connectionConfigurer, ptyProcess);
            shellConnection.postConnect();
            return shellConnection;
        } catch (IOException e) {
            logger.error("", e);
            throw new GeneralConnectionException("create shell connection error!", e);
        }
    }

}
