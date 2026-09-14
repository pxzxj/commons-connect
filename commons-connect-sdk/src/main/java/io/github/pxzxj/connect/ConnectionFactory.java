package io.github.pxzxj.connect;

public interface ConnectionFactory {

    /**
     * 判断是否支持创建此连接
     * @param connectionConfigurer 连接配置
     * @return
     */
    boolean support(ConnectionConfigurer connectionConfigurer);

    /**
     *
     * @param connectionConfigurer 连接配置
     * @return 创建成功的连接
     * @throws GeneralConnectionException 创建连接失败时抛出异常
     */
    Connection createConnection(ConnectionConfigurer connectionConfigurer) throws GeneralConnectionException;

}
