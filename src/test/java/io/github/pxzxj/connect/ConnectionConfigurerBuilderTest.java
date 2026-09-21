package io.github.pxzxj.connect;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionConfigurerBuilderTest {

	@Test
	void ssh_usesPort22ByDefault() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.ssh().build();

		assertEquals(ConnectionConfigurer.TYPE_SSH, configurer.getType());
		assertEquals(22, configurer.getPort());
	}

	@Test
	void telnet_usesConnectionConfigurerDefaultPort23() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.telnet().build();

		assertEquals(ConnectionConfigurer.TYPE_TELNET, configurer.getType());
		assertEquals(23, configurer.getPort());
	}

	@Test
	void shell_keepsShellPath() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.shell().shellPath("/bin/bash").build();

		assertEquals(ConnectionConfigurer.TYPE_SHELL, configurer.getType());
		assertEquals("/bin/bash", configurer.getShellPath());
	}

	@Test
	void type_buildsCustomType() {
		assertEquals("custom", ConnectionConfigurerBuilder.type("custom").build().getType());
	}

	@Test
	void build_appliesAllConfiguredValues() {
		CommandConfigurer postConnect = CommandConfigurerBuilder.newCommandConfigurer("whoami").build();
		CommandConfigurer preDisconnect = CommandConfigurerBuilder.newCommandConfigurer("exit").build();

		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.ssh()
				.host("10.0.0.1")
				.port(2222)
				.charset("GBK")
				.username("root")
				.password("secret")
				.postConnect(postConnect)
				.preDisconnect(preDisconnect)
				.successFlags("$ ")
				.failFlags("denied")
				.timeoutMilliSeconds(1234)
				.keepAliveInterval(4321)
				.keepAliveCommand("whoami")
				.keepAliveWaitStr("done>")
				.keepAliveWaitTimeout(777)
				.extAttrs(Collections.singletonMap("key", "value"))
				.build();

		assertEquals(ConnectionConfigurer.TYPE_SSH, configurer.getType());
		assertEquals("10.0.0.1", configurer.getHost());
		assertEquals(2222, configurer.getPort());
		assertEquals("GBK", configurer.getCharset());
		assertEquals("root", configurer.getUsername());
		assertEquals("secret", configurer.getPassword());
		assertSame(postConnect, configurer.getPostConnect());
		assertSame(preDisconnect, configurer.getPreDisconnect());
		assertEquals("$ ", configurer.getSuccessFlags()[0]);
		assertEquals("denied", configurer.getFailFlags()[0]);
		assertEquals(1234, configurer.getTimeoutMilliSeconds());
		assertEquals(4321, configurer.getKeepAliveInterval());
		assertEquals("whoami", configurer.getKeepAliveCommand());
		assertEquals("done>", configurer.getKeepAliveWaitStr());
		assertEquals(777, configurer.getKeepAliveWaitTimeout());
		assertEquals("value", configurer.getExtAttrs().get("key"));
	}

	@Test
	void build_keepsDefaults() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.telnet().build();

		assertNull(configurer.getHost());
		assertNull(configurer.getShellPath());
		assertNull(configurer.getUsername());
		assertNull(configurer.getPassword());
		assertNull(configurer.getPostConnect());
		assertNull(configurer.getPreDisconnect());
		assertNull(configurer.getSuccessFlags());
		assertNull(configurer.getFailFlags());
		assertEquals("utf-8", configurer.getCharset());
		assertEquals(5000, configurer.getTimeoutMilliSeconds());
		assertEquals(60000, configurer.getKeepAliveInterval());
		assertEquals(" ", configurer.getKeepAliveCommand());
		assertEquals(CommandConfigurer.DEFAULT_WAIT_STR, configurer.getKeepAliveWaitStr());
		assertEquals(2000, configurer.getKeepAliveWaitTimeout());
		assertTrue(configurer.getExtAttrs().isEmpty());
	}

	@Test
	void build_nullValuesDoNotOverwriteDefaults() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.telnet()
				.charset(null)
				.keepAliveCommand(null)
				.keepAliveWaitStr(null)
				.build();

		assertEquals("utf-8", configurer.getCharset());
		assertEquals(" ", configurer.getKeepAliveCommand());
		assertEquals(CommandConfigurer.DEFAULT_WAIT_STR, configurer.getKeepAliveWaitStr());
	}

	@Test
	void build_zeroValuesDoNotOverwriteDefaults() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.telnet()
				.timeoutMilliSeconds(0)
				.keepAliveInterval(0)
				.keepAliveWaitTimeout(0)
				.port(0)
				.build();

		assertEquals(5000, configurer.getTimeoutMilliSeconds());
		assertEquals(60000, configurer.getKeepAliveInterval());
		assertEquals(2000, configurer.getKeepAliveWaitTimeout());
		assertEquals(23, configurer.getPort());
	}

	@Test
	void build_emptyKeepAliveCommandIsApplied() {
		ConnectionConfigurer configurer = ConnectionConfigurerBuilder.telnet().keepAliveCommand("").build();

		assertEquals("", configurer.getKeepAliveCommand());
	}
}
