package com.mealton.command;

/**
 * Command pattern: one user action wrapped as an object that can be run and
 * undone. (Stage 1 diagram C: Command with execute() and undo().)
 */
public interface Command {

	/** Performs the action. */
	void execute();

	/** Reverses the action. */
	void undo();
}