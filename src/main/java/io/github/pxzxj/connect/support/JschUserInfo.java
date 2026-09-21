package io.github.pxzxj.connect.support;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JschUserInfo implements UserInfo, UIKeyboardInteractive {

	private final static Logger logger = LoggerFactory.getLogger(JschUserInfo.class);

	private final String password;

	public JschUserInfo(String password) {
		this.password = password;
	}

	@Override
	public String getPassphrase() {
		return null;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public boolean promptPassword(String message) {
		logger.info("promptPassword: {}", message);
		return true;
	}

	@Override
	public boolean promptPassphrase(String message) {
		logger.info("promptPassphrase: {}", message);
		return false;
	}

	@Override
	public boolean promptYesNo(String message) {
		logger.info("promptYesNo: {}", message);
		return true;
	}

	@Override
	public void showMessage(String message) {
		logger.info("message: {}", message);
	}

	@Override
	public String[] promptKeyboardInteractive(String destination,
			String name,
			String instruction,
			String[] prompt,
			boolean[] echo) {
		logger.debug("KeyboardInteractive Destination = {}", destination);
		logger.debug("KeyboardInteractive Name = {}", name);
		logger.debug("KeyboardInteractive Instruction = {}", instruction);
		return new String[]{password};
	}
}
