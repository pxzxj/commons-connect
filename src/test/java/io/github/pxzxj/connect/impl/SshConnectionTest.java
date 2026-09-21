package io.github.pxzxj.connect.impl;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;

import io.github.pxzxj.connect.CommandConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurer;
import io.github.pxzxj.connect.ConnectionConfigurerBuilder;
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

class SshConnectionTest {

	private static final int TIMEOUT = 400;

	@Test
	void isConnected_delegatesToChannelShell() throws Exception {
		ChannelShell channelShell = mock(ChannelShell.class);
		when(channelShell.isConnected()).thenReturn(true).thenReturn(false);
		SshConnection connection = newConnection(mock(Session.class), channelShell, new ByteArrayOutputStream());

		assertTrue(connection.isConnected());
		assertFalse(connection.isConnected());
	}

	@Test
	void getters_exposeSessionAndChannelShell() throws Exception {
		Session session = mock(Session.class);
		ChannelShell channelShell = mock(ChannelShell.class);

		SshConnection connection = newConnection(session, channelShell, new ByteArrayOutputStream());

		assertSame(session, connection.getSession());
		assertSame(channelShell, connection.getChannelShell());
	}

	@Test
	void close_sendsPreDisconnectCommandBeforeSessionDisconnect() throws Exception {
		Session session = mock(Session.class);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SshConnection connection = new SshConnection(session, mock(ChannelShell.class),
				configurer(preDisconnectCommand("exit", "bye>")), input("bye>"), out);

		connection.close();

		assertEquals("exit\n", out.toString("utf-8"));
		verify(session).disconnect();
	}

	@Test
	void close_preDisconnectFailureIsSwallowedAndSessionStillDisconnected() throws Exception {
		Session session = mock(Session.class);
		OutputStream broken = new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				throw new IOException("pipe broken");
			}
		};
		SshConnection connection = new SshConnection(session, mock(ChannelShell.class),
				configurer(preDisconnectCommand("exit", "bye>")), input(""), broken);

		assertDoesNotThrow(connection::close);

		verify(session).disconnect();
	}

	@Test
	void close_sessionDisconnectFailureIsSwallowed() throws Exception {
		Session session = mock(Session.class);
		doThrow(new RuntimeException("already closed")).when(session).disconnect();
		SshConnection connection = newConnection(session, mock(ChannelShell.class), new ByteArrayOutputStream());

		assertDoesNotThrow(connection::close);

		verify(session).disconnect();
	}

	private static SshConnection newConnection(Session session, ChannelShell channelShell, OutputStream out)
			throws UnsupportedEncodingException {
		return new SshConnection(session, channelShell, configurer(null), emptyInput(), out);
	}

	private static ConnectionConfigurer configurer(CommandConfigurer preDisconnect) {
		return ConnectionConfigurerBuilder.ssh()
				.timeoutMilliSeconds(TIMEOUT)
				.keepAliveCommand("")
				.preDisconnect(preDisconnect)
				.build();
	}

	private static CommandConfigurer preDisconnectCommand(String command, String successFlag) {
		// an explicit LF keeps this test platform independent, the default enter differs between platforms
		return io.github.pxzxj.connect.CommandConfigurerBuilder.newCommandConfigurer(command)
				.enter(io.github.pxzxj.connect.CommandConfigurer.BACKSLASH_N)
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
