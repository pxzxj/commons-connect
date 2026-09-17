package io.github.pxzxj.connect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandResultTest {

	@Test
	void newCommandResult_isSuccessWithoutResult() {
		CommandResult commandResult = new CommandResult();

		assertTrue(commandResult.isSuccess());
		assertNull(commandResult.getResult());
	}

	@Test
	void successfulResult_withoutResult_hasNoResult() {
		CommandResult commandResult = CommandResult.successfulResult();

		assertTrue(commandResult.isSuccess());
		assertNull(commandResult.getResult());
	}

	@Test
	void successfulResult_withResult_carriesResult() {
		CommandResult commandResult = CommandResult.successfulResult("output");

		assertTrue(commandResult.isSuccess());
		assertEquals("output", commandResult.getResult());
	}

	@Test
	void failedResult_isNotSuccess() {
		CommandResult commandResult = CommandResult.failedResult("output");

		assertFalse(commandResult.isSuccess());
		assertEquals("output", commandResult.getResult());
	}

	@Test
	void resultWithCommand_keepsCommandAndDefaultsToSuccess() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("ls").build();

		CommandResult commandResult = new CommandResult(commandConfigurer);

		assertSame(commandConfigurer, commandResult.getCommand());
		assertTrue(commandResult.isSuccess());
	}

	@Test
	void setters_overrideState() {
		CommandConfigurer commandConfigurer = CommandConfigurerBuilder.newCommandConfigurer("ls").build();
		CommandResult commandResult = new CommandResult();

		commandResult.setSuccess(false);
		commandResult.setCommand(commandConfigurer);
		commandResult.setFailCommand(commandConfigurer);
		commandResult.setResult("boom");

		assertFalse(commandResult.isSuccess());
		assertSame(commandConfigurer, commandResult.getCommand());
		assertSame(commandConfigurer, commandResult.getFailCommand());
		assertEquals("boom", commandResult.getResult());
	}
}
