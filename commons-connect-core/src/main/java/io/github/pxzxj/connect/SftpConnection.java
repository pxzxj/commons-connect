package io.github.pxzxj.connect;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.ponshine.connection.entity.SftpCommand;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Arrays;

public class SftpConnection extends AbstractFileProtocolConnection {

    private final static Logger logger = LoggerFactory.getLogger(SftpConnection.class);

    private final Session session;
    private final ChannelSftp channelSftp;

    public SftpConnection(Session session, ChannelSftp channelSftp, String connectionId, ConnectionConfigurer connectionConfigurer) {
        super(connectionId, connectionConfigurer);
        this.session = session;
        this.channelSftp = channelSftp;
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
            if(command.equals("get")) {
                args = new String[]{args[0], getDownloadMode()};
            }
            try {
                Assert.isTrue(Arrays.stream(SftpCommand.values()).anyMatch(c -> c.name().equalsIgnoreCase(command)), "command not supported");
                CommandResult cr = SftpCommand.valueOf(command.toUpperCase()).execute(channelSftp, args);
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
        return channelSftp.isConnected();
    }

    @Override
    public void close() {
        try {
            logout();
        } catch (Exception e) {
            logger.error("logout error ", e);
        }
        try {
            session.disconnect();
        } catch (Exception e) {
            logger.error("close connection error ", e);
        }
    }

}