package io.github.pxzxj.connect;

/**
 * 创建连接通用异常
 */
public class GeneralConnectionException extends RuntimeException {

	public GeneralConnectionException(String msg) {
		super(msg);
	}

	public GeneralConnectionException(String msg, Throwable cause) {
		super(msg, cause);
	}

}