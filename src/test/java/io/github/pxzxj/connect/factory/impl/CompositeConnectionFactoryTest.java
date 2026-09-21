package io.github.pxzxj.connect.factory.impl;

import io.github.pxzxj.connect.Connection;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import io.github.pxzxj.connect.ConnectionFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeConnectionFactoryTest {

	@Test
	void supports_matchesWhenAnyDelegateSupports() {
		ConnectionFactory ssh = mock(ConnectionFactory.class);
		ConnectionFactory telnet = mock(ConnectionFactory.class);
		when(telnet.supports(any())).thenReturn(true);
		CompositeConnectionFactory factory = new CompositeConnectionFactory(ssh, telnet);

		assertTrue(factory.supports(ConnectionConfigurerBuilder.telnet().build()));
		assertFalse(new CompositeConnectionFactory(ssh).supports(ConnectionConfigurerBuilder.telnet().build()));
		assertFalse(new CompositeConnectionFactory(Collections.emptyList())
				.supports(ConnectionConfigurerBuilder.telnet().build()));
	}

	@Test
	void createConnection_usesFirstSupportingDelegate() {
		Connection expected = mock(Connection.class);
		ConnectionFactory ssh = mock(ConnectionFactory.class);
		ConnectionFactory shell = mock(ConnectionFactory.class);
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.shell().build();
		when(shell.supports(configurer)).thenReturn(true);
		when(shell.createConnection(configurer)).thenReturn(expected);
		CompositeConnectionFactory factory = new CompositeConnectionFactory(Arrays.asList(ssh, shell));

		Connection actual = factory.createConnection(configurer);

		assertSame(expected, actual);
		verify(ssh, never()).createConnection(any());
	}

	@Test
	void createConnection_withoutSupportingDelegate_fallsBackToTelnetFactory() {
		CompositeConnectionFactory factory = new CompositeConnectionFactory(Collections.emptyList());

		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> factory.createConnection(ConnectionConfigurerBuilder.type("unknown").build()));

		assertEquals("host cannot be null!", exception.getMessage());
	}
}
