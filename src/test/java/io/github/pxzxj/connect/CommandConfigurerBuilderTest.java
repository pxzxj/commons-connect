package io.github.pxzxj.connect;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandConfigurerBuilderTest {

	@Test
	void build_keepsCommandDefaults() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("ls").build();

		assertEquals("ls", commandConfigurer.getCommand());
		assertNull(commandConfigurer.getCommandFunction());
		assertEquals(5000, commandConfigurer.getTimeoutMilliSeconds());
		assertEquals(CommandConfigurer.BACKSLASH_N, commandConfigurer.getEnter());
		assertEquals(" ", commandConfigurer.getMoreCommand());
		assertNull(commandConfigurer.getMoreFlag());
		assertNull(commandConfigurer.getSuccessFlags());
		assertNull(commandConfigurer.getFailFlags());
		assertNull(commandConfigurer.getNext());
	}

	@Test
	void build_appliesConfiguredValues() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("cat big")
				.timeoutMilliSeconds(60000)
				.enter("\r\n")
				.successFlags("$ ", "# ")
				.failFlags("error")
				.moreFlag("--More--")
				.moreCommand("q")
				.build();

		assertEquals(60000, commandConfigurer.getTimeoutMilliSeconds());
		assertEquals(CommandConfigurer.BACKSLASH_RN, commandConfigurer.getEnter());
		assertArrayEquals(new String[]{"$ ", "# "}, commandConfigurer.getSuccessFlags());
		assertArrayEquals(new String[]{"error"}, commandConfigurer.getFailFlags());
		assertEquals("--More--", commandConfigurer.getMoreFlag());
		assertEquals("q", commandConfigurer.getMoreCommand());
	}

	@Test
	void build_zeroTimeoutAndNullMoreCommandKeepDefaults() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("ls")
				.timeoutMilliSeconds(0)
				.moreCommand(null)
				.build();

		assertEquals(5000, commandConfigurer.getTimeoutMilliSeconds());
		assertEquals(" ", commandConfigurer.getMoreCommand());
	}

	@Test
	void build_emptyEnterIsApplied() {
		assertEquals("", CommandConfigurerBuilder.newCommandConfigurer("ls").enter("").build().getEnter());
	}

	@Test
	void build_commandFunctionLeavesCommandNull() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder
				.newCommandConfigurer((Function<String, String>) echo -> "select " + echo)
				.build();

		assertNull(commandConfigurer.getCommand());
		assertNotNull(commandConfigurer.getCommandFunction());
		assertEquals("select id", commandConfigurer.getCommandFunction().apply("id"));
	}

	@Test
	void build_nextChainIsReturnedFromHeadInOrder() {
		CommandConfigurer head = CommandConfigurerBuilder.newCommandConfigurer("first")
				.timeoutMilliSeconds(100)
				.successFlags("a>")
				.next("second")
				.timeoutMilliSeconds(200)
				.successFlags("b>")
				.next("third")
				.timeoutMilliSeconds(300)
				.successFlags("c>")
				.build();

		assertEquals("first", head.getCommand());
		assertEquals(100, head.getTimeoutMilliSeconds());
		assertArrayEquals(new String[]{"a>"}, head.getSuccessFlags());

		CommandConfigurer second = head.getNext();
		assertEquals("second", second.getCommand());
		assertEquals(200, second.getTimeoutMilliSeconds());
		assertArrayEquals(new String[]{"b>"}, second.getSuccessFlags());

		CommandConfigurer third = second.getNext();
		assertEquals("third", third.getCommand());
		assertEquals(300, third.getTimeoutMilliSeconds());
		assertArrayEquals(new String[]{"c>"}, third.getSuccessFlags());
		assertNull(third.getNext());
	}

	@Test
	void build_eachBuildReturnsIndependentInstance() {
		CommandConfigurerBuilder builder = CommandConfigurerBuilder.newCommandConfigurer("ls");
		CommandConfigurer first = builder.build();
		CommandConfigurer second = builder.build();

		first.setCommand("changed");

		assertEquals("ls", second.getCommand());
	}
}
