package io.github.pxzxj.connect.impl;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
import org.apache.commons.net.telnet.TelnetClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelnetConnectionTest {

	private static final int TIMEOUT = 400;

	@Test
	void isConnected_delegatesToTelnetClient() throws Exception {
		TelnetClient telnetClient = mock(TelnetClient.class);
		when(telnetClient.isConnected()).thenReturn(true).thenReturn(false);
		TelnetConnection connection = newConnection(telnetClient, new ByteArrayOutputStream());

		assertTrue(connection.isConnected());
		assertFalse(connection.isConnected());
	}

	@Test
	void getTelnetClient_exposesClient() throws Exception {
		TelnetClient telnetClient = mock(TelnetClient.class);

		TelnetConnection connection = newConnection(telnetClient, new ByteArrayOutputStream());

		assertSame(telnetClient, connection.getTelnetClient());
	}

	@Test
	void close_sendsPreDisconnectCommandBeforeClientDisconnect() throws Exception {
		TelnetClient telnetClient = mock(TelnetClient.class);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TelnetConnection connection = new TelnetConnection(
				configurer(preDisconnectCommand("exit", "bye>")), input("bye>"), out, telnetClient);

		connection.close();

		assertEquals("exit\n", out.toString("utf-8"));
		verify(telnetClient).disconnect();
	}

	@Test
	void close_preDisconnectFailureIsSwallowedAndClientStillDisconnected() throws Exception {
		TelnetClient telnetClient = mock(TelnetClient.class);
		OutputStream broken = new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				throw new IOException("pipe broken");
			}
		};
		TelnetConnection connection = new TelnetConnection(
				configurer(preDisconnectCommand("exit", "bye>")), input(""), broken, telnetClient);

		assertDoesNotThrow(connection::close);

		verify(telnetClient).disconnect();
	}

	@Test
	void close_clientDisconnectFailureIsSwallowed() throws Exception {
		TelnetClient telnetClient = mock(TelnetClient.class);
		doThrow(new IOException("already closed")).when(telnetClient).disconnect();
		TelnetConnection connection = newConnection(telnetClient, new ByteArrayOutputStream());

		assertDoesNotThrow(connection::close);

		verify(telnetClient).disconnect();
	}

	private static TelnetConnection newConnection(TelnetClient telnetClient, OutputStream out)
			throws UnsupportedEncodingException {
		return new TelnetConnection(configurer(null), emptyInput(), out, telnetClient);
	}

	private static ConnectionConfigurer configurer(CommandConfigurer preDisconnect) {
		return ConnectionConfigurerBuilder.telnet()
				.id("telnet-1")
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.preDisconnect(preDisconnect)
				.build();
	}

	private static CommandConfigurer preDisconnectCommand(String command, String successFlag) {
		return io.github.pxzxj.connect.CommandConfigurerBuilder.newCommandConfigurer(command)
				.timeoutMilliSeconds(TIMEOUT)
				.successFlags(successFlag)
				.build();
	}

	private static InputStream input(String text) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(text.getBytes("utf-8"));
	}

	private static InputStream emptyInput() {
		return new ByteArrayInputStream(new byte[0]);
	}
}
