package com.mealton.command;

import java.util.ArrayDeque;
import java.util.Deque;

/** Command pattern invoker: runs commands and keeps the undo/redo stacks. */
public class CommandHistory {
	private final Deque<Command> undoStack = new ArrayDeque<>();
	private final Deque<Command> redoStack = new ArrayDeque<>();

	/** Executes a command, records it for undo and discards any redo history. */
	public void execute(Command cmd) {
		cmd.execute();
		undoStack.push(cmd);
		redoStack.clear();
	}

	/** @return true if something was undone */
	public boolean undo() {
		if (undoStack.isEmpty()) {
			return false;
		}
		Command cmd = undoStack.pop();
		cmd.undo();
		redoStack.push(cmd);
		return true;
	}

	/** @return true if something was redone */
	public boolean redo() {
		if (redoStack.isEmpty()) {
			return false;
		}
		Command cmd = redoStack.pop();
		cmd.execute();
		undoStack.push(cmd);
		return true;
	}

	/**
	 * Forgets everything that could be undone or redone (used after loading saved
	 * data).
	 */
	public void clear() {
		undoStack.clear();
		redoStack.clear();
	}

	public boolean canUndo() {
		return !undoStack.isEmpty();
	}

	public boolean canRedo() {
		return !redoStack.isEmpty();
	}
}