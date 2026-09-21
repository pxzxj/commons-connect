package io.github.pxzxj.connect.factory.impl;

import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.GeneralConnectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshConnectionFactoryTest {

	private final SshConnectionFactory factory = new SshConnectionFactory();

	@Test
	void supports_matchesSshTypeIgnoringCase() {
		assertTrue(factory.supports(ConnectionConfigurerBuilder.ssh().build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("SSH").build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("ssh").build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.telnet().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.shell().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.type(null).build()));
	}

	@Test
	void createConnection_withoutUsername_failsFast() {
		NullPointerException exception = assertThrows(NullPointerException.class, () -> factory.createConnection(
				ConnectionConfigurerBuilder.ssh().host("127.0.0.1").password("secret").build()));

		assertEquals("username cannot be null!", exception.getMessage());
	}

	@Test
	void createConnection_withoutHost_failsFast() {
		NullPointerException exception = assertThrows(NullPointerException.class, () -> factory.createConnection(
				ConnectionConfigurerBuilder.ssh().username("root").password("secret").build()));

		assertEquals("host cannot be null!", exception.getMessage());
	}

	@Test
	void createConnection_withoutPasswordAndPrivateKey_failsWithGeneralConnectionException() {
		GeneralConnectionException exception = assertThrows(GeneralConnectionException.class, () -> factory.createConnection(
				ConnectionConfigurerBuilder.ssh().host("127.0.0.1").port(1).username("root").build()));

		assertTrue(exception.getMessage().contains("create ssh connection error"));
	}
}
