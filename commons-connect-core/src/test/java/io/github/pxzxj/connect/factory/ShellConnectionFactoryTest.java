package io.github.pxzxj.connect.factory;

import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellConnectionFactoryTest {

	private final ShellConnectionFactory factory = new ShellConnectionFactory();

	@Test
	void supports_matchesShellTypeIgnoringCase() {
		assertTrue(factory.supports(ConnectionConfigurerBuilder.shell().build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("SHELL").build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("shell").build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.ssh().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.telnet().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.type(null).build()));
	}
}
