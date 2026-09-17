package io.github.pxzxj.connect.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JschUserInfoTest {

	@Test
	void getPassword_returnsConfiguredPassword() {
		assertEquals("secret", new JschUserInfo("secret").getPassword());
	}

	@Test
	void getPassphrase_returnsNull() {
		assertNull(new JschUserInfo("secret").getPassphrase());
	}

	@Test
	void promptYesNo_acceptsAnyHostKey() {
		assertTrue(new JschUserInfo("secret").promptYesNo("The authenticity of host '10.0.0.1' can't be established."));
	}

	@Test
	void promptPassword_returnsTrueAndPromptPassphraseReturnsFalse() {
		JschUserInfo userInfo = new JschUserInfo("secret");

		assertTrue(userInfo.promptPassword("password:"));
		assertFalse(userInfo.promptPassphrase("passphrase:"));
	}

	@Test
	void promptKeyboardInteractive_returnsPassword() {
		JschUserInfo userInfo = new JschUserInfo("secret");

		assertArrayEquals(new String[]{"secret"}, userInfo.promptKeyboardInteractive("destination", "name", "instruction",
				new String[]{"password:"}, new boolean[]{false}));
	}

	@Test
	void showMessage_doesNotThrow() {
		assertDoesNotThrow(() -> new JschUserInfo("secret").showMessage("hello"));
	}
}
