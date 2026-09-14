package io.github.pxzxj.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ponshine.connection.event.ConnectionClosedEvent;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public abstract class ConnectionBase implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionBase.class);

    private final static String DEFAULT_WAITSTR = "(!@#$%)";
    private final static int DEFAULT_WAIT_MILLISECONDS = 5000;
    private final static String DEFAULT_ENTER = CommandConfigurer.BACKSLASH_N;

    private final String connectionId;
    private final ConnectionConfigurer connectionConfigurer;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Writer writer;
    private final StringBuffer fullScreen = new StringBuffer();
    private String lastScreen;
    private byte[] lastScreenBytes;
    private final long createTime;
    private volatile long lastAccessTime;
    private volatile boolean keepActive = true;
    private final ObjectMapper om = new ObjectMapper();
    private ApplicationEventPublisher applicationEventPublisher;


    public ConnectionBase(String connectionId, ConnectionConfigurer connectionConfigurer, InputStream inputStream, OutputStream outputStream) throws UnsupportedEncodingException {
        this.connectionId = connectionId;
        this.connectionConfigurer = connectionConfigurer;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.writer = new OutputStreamWriter(outputStream, connectionConfigurer.getCharset());
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
    }

    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public void sendString(String command) {
        sendString(command, DEFAULT_ENTER);
    }

    public synchronized void sendString(String command, String enter) {
        try {
            command += enter;
            logger.info("host: " + connectionConfigurer.getHost() + ", send command: " + om.writeValueAsString(command));
            lastAccessTime = System.currentTimeMillis();
            writer.write(command);
            writer.flush();
        } catch (IOException e) {
            logger.error("command send fail: " + command, e);
            throw new GeneralExecException("command send fail: " + command, e);
        }
    }

    @Override
    public CommandResult sendStringWithResult(String command) {
        return sendStringWithResult(command, DEFAULT_WAITSTR, DEFAULT_WAIT_MILLISECONDS);
    }

    @Override
    public synchronized CommandResult sendStringWithResult(String command, String waitStr, int maxMilliSeconds) {
        if (waitStr == null) {
            waitStr = DEFAULT_WAITSTR;
        }
        sendString(command);
        boolean b = waitForString(waitStr, maxMilliSeconds);
        CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer(command).successFlags(waitStr).timeoutMilliSeconds(maxMilliSeconds).build();
        return new CommandResult(b, commandConfigurer, lastScreen);
    }

    @Override
    public synchronized CommandResult sendCommand(CommandConfigurer commandConfigurer) {
        CommandConfigurer cc = commandConfigurer;
        if (cc.getCommand() == null) {
            return CommandResult.failedResult("第一条命令不能为空");
        }
        CommandResult commandResult = new CommandResult(commandConfigurer);
        String echo = "";
        byte[] echoBytes = new byte[0];
        while (cc != null) {
            if (cc.getCommand() == null && cc.getCommandFunction() == null) {
                return CommandResult.failedResult("command和commandFunction不能同时为空");
            }
            String[] successFlags = cc.getSuccessFlags();
            String[] failFlags = cc.getFailFlags();
            int timeoutMilliSeconds = cc.getTimeoutMilliSeconds();
            String enter = cc.getEnter();
            String moreFlag = cc.getMoreFlag();
            String moreCommand = cc.getMoreCommand();
            String command = cc.getCommand() != null ? cc.getCommand() : cc.getCommandFunction().apply(echo);
            sendString(command, enter);
            if (successFlags != null || failFlags != null) {
                boolean b = waitForString(successFlags, failFlags, timeoutMilliSeconds, moreFlag, moreCommand);
                echo += lastScreen;
                echoBytes = ArrayUtils.addAll(echoBytes, lastScreenBytes);
                if (!b) {
                    commandResult.setSuccess(false);
                    commandResult.setFailCommand(cc);
                    break;
                }
            } else {
                waitForString(DEFAULT_WAITSTR, timeoutMilliSeconds, moreFlag, moreCommand);
                echo += lastScreen;
                echoBytes = ArrayUtils.addAll(echoBytes, lastScreenBytes);
            }
            cc = cc.getNext();
        }
        commandResult.setResult(echo);
        commandResult.setResultStream(new ByteArrayInputStream(echoBytes));
        return commandResult;
    }

    public synchronized boolean waitForString(String[] successFlags, String[] failFlags, int maxMilliSeconds, String moreFlag, String moreCommand) {
        if (successFlags == null) {
            successFlags = new String[0];
        }
        if (failFlags == null) {
            failFlags = new String[0];
        }
        long startTime = lastAccessTime = System.currentTimeMillis();
        logger.info("host: " + connectionConfigurer.getHost() + ", begin waitting for string: " + Arrays.toString(successFlags) + " and " + Arrays.toString(failFlags));
        boolean success = false;
        byte[] totalData = new byte[0];
        int availableSize;
        try {
            byte[][] successMatchByteArray = prepareMatchBytes(successFlags);
            byte[][] failMatchByteArray = prepareMatchBytes(failFlags);
            while (System.currentTimeMillis() - startTime <= maxMilliSeconds) {
                int totalSize = totalData.length;
                if ((availableSize = inputStream.available()) > 0) {
                    byte[] data = new byte[availableSize];
                    int read = inputStream.read(data);
                    if (read == -1) {
                        break;
                    }
                    totalData = ArrayUtils.addAll(totalData, data);
                    boolean failMatch = matchEnd(totalData, failMatchByteArray, totalSize);
                    boolean successMatch = successFlags.length == 0 || matchEnd(totalData, successMatchByteArray, totalSize);
                    if (successMatch && !failMatch) {
                        //匹配成功标识且不匹配失败标识才视作成功
                        success = true;
                    }
                    if (failMatch || success) {
                        //成功匹配后再取出流中剩余数据
                        if ((availableSize = inputStream.available()) > 0) {
                            data = new byte[availableSize];
                            read = inputStream.read(data);
                            if(read != -1) {
                                totalData = ArrayUtils.addAll(totalData, data);
                            }
                        }
                        break;
                    }
                } else {
                    if (moreFlag != null) {
                        sendString(moreCommand, "");
                    } else if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval()) {
                        lastAccessTime = System.currentTimeMillis();
                        sendString(connectionConfigurer.getKeepActiveCommand(), "");
                    }
                    TimeUnit.MILLISECONDS.sleep(200);
                }
            }
            lastScreenBytes = totalData;
            lastScreen = new String(totalData, connectionConfigurer.getCharset());
            fullScreen.append(lastScreen);
        } catch (Exception e) {
            throw new GeneralExecException("Error exception accur while waitting for string", e);
        }
        lastAccessTime = System.currentTimeMillis();
        return success;
    }

    private boolean matchEnd(byte[] totalData, byte[][] matchByteArray, int startIndex) {
        for (byte[] data : matchByteArray) {
            if (match(totalData, data, startIndex)) {
                return true;
            }
        }
        return false;
    }

    private boolean match(byte[] totalData, byte[] data, int startIndex) {
        boolean find = false;
        int length = data.length;
        if (startIndex >= length) {
            startIndex = startIndex - length;
        }
        byte head = data[0];
        while (startIndex < totalData.length - length) {
            startIndex = ArrayUtils.indexOf(totalData, head, startIndex);
            if (startIndex == -1) {
                break;
            } else if (Arrays.equals(ArrayUtils.subarray(totalData, startIndex, startIndex + length), data)) {
                find = true;
                break;
            } else {
                startIndex++;
            }
        }
        return find;
    }

    private byte[][] prepareMatchBytes(String[] Flags) throws UnsupportedEncodingException {
        byte[][] matchByteArray = new byte[Flags.length][];
        int index = 0;
        for (String s : Flags) {
            matchByteArray[index++] = s.getBytes(connectionConfigurer.getCharset());
        }
        return matchByteArray;
    }

    @Override
    public boolean waitForString(String string, int maxMilliSeconds) {
        return waitForString(new String[]{string}, null, maxMilliSeconds, null, null);
    }

    public boolean waitForString(String string, int maxMilliSeconds, String moreFlag, String moreCommand) {
        return waitForString(new String[]{string}, null, maxMilliSeconds, moreFlag, moreCommand);
    }

    public boolean waitForString(String[] stringArr, int maxMilliSeconds) {
        return waitForString(stringArr, null, maxMilliSeconds, null, null);
    }

    public boolean waitForString(String[] stringArr, int maxMilliSeconds, String moreFlag, String moreCommand) {
        return waitForString(stringArr, null, maxMilliSeconds, moreFlag, moreCommand);
    }

    @Override
    public String getLastScreen() {
        return lastScreen;
    }

    @Override
    public String getFullScreen() {
        return fullScreen.toString();
    }

    @Override
    public void cleanScreen() {
        fullScreen.setLength(0);
    }

    public void postConnect() {
        String[] successFlags = connectionConfigurer.getSuccessFlags();
        String[] failFlags = connectionConfigurer.getFailFlags();
        int timeoutMilliSeconds = connectionConfigurer.getTimeoutMilliSeconds();
        if (successFlags != null || failFlags != null) {
            boolean b = waitForString(successFlags, failFlags, timeoutMilliSeconds, null, null);
            if (!b) {
                if(isConnected()){
                    close();
                }
                throw new GeneralConnectionException("连接失败, 回显内容:" + getFullScreen());
            }
        } else {
            waitForString(DEFAULT_WAITSTR, timeoutMilliSeconds);
        }
        CommandConfigurer postConnectCommand = connectionConfigurer.getPostConnect();
        if (postConnectCommand != null) {
            if (postConnectCommand.getCommand() == null && postConnectCommand.getCommandFunction() != null) {
                postConnectCommand.setCommand(postConnectCommand.getCommandFunction().apply(lastScreen));
            }
            CommandResult commandResult = sendCommand(postConnectCommand);
            if (!commandResult.isSuccess()) {
                if(isConnected()){
                    close();
                }
                String message = "连接后执行命令失败：" + commandResult.getFailCommand() + "\n回显内容：" + commandResult.getResult();
                throw new GeneralExecException(message, commandResult);
            }
        }
        logger.info("host: " + connectionConfigurer.getHost() + ", login output: " + getFullScreen());
        if(StringUtils.hasLength(connectionConfigurer.getKeepActiveCommand())){
            Thread thread = new Thread(new KeepActiveDaemon());
            thread.setName("KeepActiveDaemon " + connectionConfigurer.getHost() + ":" + connectionConfigurer.getPort());
            thread.setDaemon(true);
            thread.start();
        }
    }

    protected synchronized void logout() {
        keepActive = false;
        if (connectionConfigurer.getPreLogout() != null) {
            this.sendCommand(connectionConfigurer.getPreLogout());
        }
        if(applicationEventPublisher != null){
            applicationEventPublisher.publishEvent(new ConnectionClosedEvent(this));
        }
    }

    @Override
    public ConnectionConfigurer getConnectionConfigurer() {
        return connectionConfigurer;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public synchronized long getLastAccessTime() {
        return lastAccessTime;
    }

    private class KeepActiveDaemon implements Runnable {

        /**
         * 最后一次发送保活命令时间
         */
        private long lastSendKeepActiveCommandTime = System.currentTimeMillis();

        @Override
        public void run() {
            logger.info(Thread.currentThread().getName() + " start");
            try {
                String waitStr = connectionConfigurer.getKeepActiveWaitStr() != null ? connectionConfigurer.getKeepActiveWaitStr() : DEFAULT_WAITSTR;
                CommandConfigurer keepActiveCommand = CommandConfigurerBuilder.newCommandConfigurer(connectionConfigurer.getKeepActiveCommand())
                                                                        .enter("")
                                                                        .successFlags(waitStr)
                                                                        .timeoutMilliSeconds(connectionConfigurer.getKeepActiveWaitTimeout())
                                                                        .build();
                while (keepActive) {
                    if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval() &&
                        System.currentTimeMillis() - lastSendKeepActiveCommandTime > connectionConfigurer.getKeepActiveInterval()) {
                        synchronized (ConnectionBase.this){
                            if (System.currentTimeMillis() - lastAccessTime > connectionConfigurer.getKeepActiveInterval() &&
                                    System.currentTimeMillis() - lastSendKeepActiveCommandTime > connectionConfigurer.getKeepActiveInterval()) {
                                //保活命令不应该修改lastAccessTime的值，因此临时保存，保活命令发送完成后恢复
                                long tempLastAccessTime = lastAccessTime;
                                lastSendKeepActiveCommandTime = System.currentTimeMillis();
                                CommandResult commandResult = sendCommand(keepActiveCommand);
                                logger.info("host: " + connectionConfigurer.getHost() + ", keepActiveResult: " + commandResult);
                                lastAccessTime = tempLastAccessTime;
                            }
                        }
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                logger.error(Thread.currentThread().getName(), e);
                if (!isConnected()) {
                    logger.error(Thread.currentThread().getName() + " Exit, Connection Closed");
                }
            }
            logger.info(Thread.currentThread().getName() + " Terminated");
        }
    }

}
