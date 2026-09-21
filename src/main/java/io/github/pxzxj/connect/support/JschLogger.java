package io.github.pxzxj.connect.support;

import com.jcraft.jsch.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 * @since 2.0.1
 */
public class JschLogger implements Logger {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JschLogger.class);

	public boolean isEnabled(int level) {
		switch (level) {
			case Logger.INFO:
				return LOGGER.isInfoEnabled();
			case Logger.WARN:
				return LOGGER.isWarnEnabled();
			case Logger.DEBUG:
				return LOGGER.isDebugEnabled();
			case Logger.ERROR:
			case Logger.FATAL:
				return LOGGER.isErrorEnabled();
			default:
				return false;
		}
	}

	public void log(int level, String message) {
		switch (level) {
			case Logger.INFO:
				LOGGER.info(message);
				break;
			case Logger.WARN:
				LOGGER.warn(message);
				break;
			case Logger.DEBUG:
				LOGGER.debug(message);
				break;
			case Logger.ERROR:
			case Logger.FATAL:
				LOGGER.error(message);
				break;
			default:
				break;
		}
	}

}
