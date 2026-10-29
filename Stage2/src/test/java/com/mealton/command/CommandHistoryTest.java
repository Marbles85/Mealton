package com.mealton.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandHistoryTest {

	/** A fake command that just counts how many times it was run and undone. */
	private static class CountingCommand implements Command {
		int executed;
		int undone;

		@Override
		public void execute() {
			executed++;
		}

		@Override
		public void undo() {
			undone++;
		}
	}

	@Test
	void executeThenUndoThenRedo() {
		CommandHistory history = new CommandHistory();
		CountingCommand c = new CountingCommand();

		history.execute(c);
		assertEquals(1, c.executed);
		assertTrue(history.canUndo());
		assertFalse(history.canRedo());

		assertTrue(history.undo());
		assertEquals(1, c.undone);
		assertTrue(history.canRedo());

		assertTrue(history.redo());
		assertEquals(2, c.executed);
	}

	@Test
	void undoAndRedoOnEmptyHistoryDoNothing() {
		CommandHistory history = new CommandHistory();
		assertFalse(history.undo());
		assertFalse(history.redo());
	}

	@Test
	void newCommandClearsRedoStack() {
		CommandHistory history = new CommandHistory();
		history.execute(new CountingCommand());
		history.undo();
		assertTrue(history.canRedo());

		history.execute(new CountingCommand());
		assertFalse(history.canRedo());
	}

	@Test
	void commandsAreUndoneInReverseOrder() {
		CommandHistory history = new CommandHistory();
		CountingCommand first = new CountingCommand();
		CountingCommand second = new CountingCommand();
		history.execute(first);
		history.execute(second);

		history.undo();
		assertEquals(1, second.undone);
		assertEquals(0, first.undone);
	}
}