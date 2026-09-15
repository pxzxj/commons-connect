package io.github.pxzxj.connect.factory;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import io.github.pxzxj.connect.*;
import io.github.pxzxj.connect.support.SystemInfoRt;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ShellConnectionFactory implements ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ShellConnectionFactory.class);

    @Override
    public boolean support(ConnectionConfigurer connectionConfigurer) {
        return ConnectionConfigurer.TYPE_SHELL.equalsIgnoreCase(connectionConfigurer.getType());
    }

    @Override
    public Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException {
        String shellPath = connectionConfigurer.getShellPath();
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
            String connectionId = StringUtils.isNotBlank(connectionConfigurer.getId()) ? connectionConfigurer.getId() : ptyProcess.pid() + "";
            ShellConnection shellConnection = new ShellConnection(connectionId, connectionConfigurer, ptyProcess);
            shellConnection.postConnect();
            return shellConnection;
        } catch (IOException e) {
            logger.error("", e);
            throw new GeneralConnectionException("create shell connection fail!", e);
        }
    }

}