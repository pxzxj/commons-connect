package io.github.pxzxj.connect.factory;

import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelnetConnectionFactoryTest {

	private final TelnetConnectionFactory factory = new TelnetConnectionFactory();

	@Test
	void supports_matchesTelnetTypeIgnoringCase() {
		assertTrue(factory.supports(ConnectionConfigurerBuilder.telnet().build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("TELNET").build()));
		assertTrue(factory.supports(ConnectionConfigurerBuilder.type("telnet").build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.ssh().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.shell().build()));
		assertFalse(factory.supports(ConnectionConfigurerBuilder.type(null).build()));
	}

	@Test
	void createConnection_withoutHost_failsFast() {
		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> factory.createConnection(ConnectionConfigurerBuilder.telnet().build()));

		assertEquals("host cannot be null!", exception.getMessage());
	}
}
