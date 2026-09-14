package io.github.pxzxj.connect;


import com.ponshine.connection.entity.FtpCommand;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.File;
import java.util.Arrays;

public class FtpConnection extends AbstractFileProtocolConnection {

    private final Logger logger = LoggerFactory.getLogger(FtpConnection.class);

    private final FTPClient ftp;
    private final String localPath;

    public FtpConnection(String connectionId, FTPClient ftp, ConnectionConfigurer connectionConfigurer) {
        super(connectionId, connectionConfigurer);
        this.ftp = ftp;
        localPath = connectionConfigurer.getExtAttrs().get(ConnectionConfigurer.DEFAULT_LOCAL_PATH_ATTR);
    }

    @Override
    public CommandResult sendCommand(CommandConfigurer commandConfigurer) {
        lastAccessTime = System.currentTimeMillis();
        CommandConfigurer cc = commandConfigurer;
        Assert.notNull(cc.getCommand(), "first command cannot be null");
        CommandResult commandResult = new CommandResult(commandConfigurer);
        while (cc != null) {
            String fullCommand = cc.getCommand();
            Assert.notNull(fullCommand, "command cannot be null");
            String[] commandAndArgs = fullCommand.split("\\s+");
            if(commandAndArgs.length == 0) {
                break;
            }
            final String command = commandAndArgs[0];
            String[] args = Arrays.copyOfRange(commandAndArgs, 1, commandAndArgs.length);
            // GET命令的参数只能是文件名，不能包含目录
            if(command.equals("get")) {
                args = new String[]{args[0], getDownloadMode()};
                if(localPath != null) {
                    args[0] = new File(localPath, args[0]).getAbsolutePath();
                }
            } else if(command.equals("put") && localPath != null) {
                args[0] = new File(localPath, args[0]).getAbsolutePath();
            }
            try {
                Assert.isTrue(Arrays.stream(FtpCommand.values()).anyMatch(c -> c.name().equalsIgnoreCase(command)), "command not supported");
                CommandResult cr = FtpCommand.valueOf(command.toUpperCase()).execute(ftp, args);
                commandResult.setSuccess(cr.isSuccess());
                commandResult.setResult(cr.getResult());
                commandResult.setResultStream(cr.getResultStream());
                if(!cr.isSuccess()) {
                    commandResult.setFailCommand(cc);
                    break;
                }

            } catch (Exception e) {
                logger.error("", e);
                commandResult.setSuccess(false);
                commandResult.setResult(ExceptionUtils.getStackTrace(e));
                commandResult.setFailCommand(cc);
                break;
            }
            cc = cc.getNext();
        }
        return commandResult;
    }

    @Override
    public boolean isConnected() {
        return ftp.isConnected();
    }

    @Override
    public void close() {
        try {
            logout();
        } catch (Exception e) {
            logger.error("logout error ", e);
        }
        try {
            ftp.disconnect();
        } catch (Exception e) {
            logger.error("close connection error ", e);
        }
    }
}
