package io.github.pxzxj.connect;

/**
 * Common exception for connection creation failures
 */
public class GeneralConnectionException extends RuntimeException {

	public GeneralConnectionException(String msg) {
		super(msg);
	}

	public GeneralConnectionException(String msg, Throwable cause) {
		super(msg, cause);
	}

}