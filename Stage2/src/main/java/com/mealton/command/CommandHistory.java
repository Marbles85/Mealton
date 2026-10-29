package com.mealton.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Command pattern invoker: runs commands and keeps the undo and redo stacks.
 */
public class CommandHistory {

	// A Deque used as a stack: the last command added is the first one undone
	private final Deque<Command> undoStack = new ArrayDeque<>();
	private final Deque<Command> redoStack = new ArrayDeque<>();

	/** Runs a command, remembers it for undo, and forgets any old redo history. */
	public void execute(Command cmd) {
		cmd.execute();
		undoStack.push(cmd);
		redoStack.clear(); // a new action makes the old "redo" steps invalid
	}

	/** Undoes the most recent command. Returns true if something was undone. */
	public boolean undo() {
		if (undoStack.isEmpty()) {
			return false;
		}
		Command cmd = undoStack.pop();
		cmd.undo();
		redoStack.push(cmd);
		return true;
	}

	/**
	 * Re-does the command that was just undone. Returns true if something was
	 * redone.
	 */
	public boolean redo() {
		if (redoStack.isEmpty()) {
			return false;
		}
		Command cmd = redoStack.pop();
		cmd.execute();
		undoStack.push(cmd);
		return true;
	}

	public boolean canUndo() {
		return !undoStack.isEmpty();
	}

	public boolean canRedo() {
		return !redoStack.isEmpty();
	}
}