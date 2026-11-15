package com.mealton.agent.tools;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.ToolSpec;
import com.mealton.plan.CalendarService.PrepEvent;
import com.mealton.plan.PrepScheduler;
import com.mealton.plan.PrepScheduler.Row;
import com.mealton.plan.PrepScheduler.Schedule;
import com.mealton.plan.PrepScheduler.Task;

/**
 * Lets the agent read the prep days on the calendar, preview what a prep
 * session would look like, and save, move or delete prep days. The schedule is
 * calculated by Mealton's own scheduler, not written by the LLM.
 */
public class PrepDayTool implements Tool {
	private final MealtonFacade facade;

	public PrepDayTool(MealtonFacade facade) {
		this.facade = facade;
	}

	@Override
	public String name() {
		return "prep_day";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: list, preview, save, delete.");
		params.put("prep_date", "For preview: the day to cook. Defaults to today.");
		params.put("start_time", "For preview: when to start, like 10:00. Defaults to 10:00.");
		params.put("from", "For preview: first day the meals are for. Defaults to the day after prep_date.");
		params.put("to", "For preview: last day the meals are for. Defaults to 4 days after 'from'.");
		params.put("limit_minutes",
				"For preview and save: how long the user has, in minutes (15 to 720). Defaults to 120.");
		params.put("replace_date", "For save: the date of an existing prep day to move to the new prep_date (an edit). "
				+ "Omit to add a new prep day.");
		params.put("date", "For delete: the date of the prep day to take off the calendar.");
		return new ToolSpec(name(),
				"Prep days (batch cooking). 'list' shows the prep days on the calendar. 'preview' calculates the "
						+ "schedule for a prep session: the steps with their times, which recipes had to be left out to "
						+ "fit the time limit, and what is missing from the pantry, and saves nothing. 'save' puts that "
						+ "prep day on the calendar (give replace_date to move an existing one instead of adding a second), "
						+ "and 'delete' takes a prep day off. The schedule is calculated by Mealton from the calendar meals, "
						+ "you cannot edit its steps; change the dates, time or limit instead. Preview first, then save "
						+ "only when the user agrees.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "list":
				return list();
			case "preview":
				return preview(args, false);
			case "save":
				return preview(args, true);
			case "delete": {
				LocalDate d = AgentDates.parse(args.get("date"), facade.today(), null);
				if (d == null) {
					return ToolResult.error("Give the date of the prep day to delete.");
				}
				return facade.removePrepDay(d)
						? ToolResult.ok("Took the prep day off " + AgentDates.show(d)
								+ ". 'undo' on the calendar tool brings it back.")
						: ToolResult.error("There is no prep day on " + AgentDates.show(d) + ".");
			}
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use list, preview, save or delete.");
			}
		} catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private ToolResult list() {
		List<PrepEvent> all = facade.getPrepDays();
		if (all.isEmpty()) {
			return ToolResult.ok("There are no prep days on the calendar.");
		}
		StringBuilder sb = new StringBuilder("Prep days on the calendar:");
		all.stream().sorted(Comparator.comparing(PrepEvent::date))
				.forEach(e -> sb.append("\n- ").append(AgentDates.show(e.date())).append(" ").append(e.start())
						.append("-").append(e.end()).append(", ").append(e.meals()).append(" portions"));
		return ToolResult.ok(sb.toString());
	}

	private ToolResult preview(Map<String, String> args, boolean save) {
		LocalDate today = facade.today();
		LocalDate prep = AgentDates.parse(args.get("prep_date"), today, today);
		LocalDate from = AgentDates.parse(args.get("from"), today, prep.plusDays(1));
		LocalDate to = AgentDates.parse(args.get("to"), today, from.plusDays(4));
		LocalTime start = LocalTime.parse(args.getOrDefault("start_time", "10:00").trim().length() == 4
				? "0" + args.getOrDefault("start_time", "10:00").trim()
				: args.getOrDefault("start_time", "10:00").trim());
		int limit = 120;
		String l = args.get("limit_minutes");
		if (l != null && !l.isBlank()) {
			try {
				limit = Integer.parseInt(l.trim());
			} catch (NumberFormatException e) {
				return ToolResult.error("'" + l + "' is not a number of minutes.");
			}
			if (limit < 15 || limit > 720) {
				return ToolResult.error("limit_minutes has to be between 15 and 720.");
			}
		}
		String problem = PrepScheduler.error(prep, from, to);
		if (!problem.isEmpty()) {
			return ToolResult.error(problem);
		}
		Schedule s = facade.buildPrepSchedule(new PrepScheduler.Options(prep, start, from, to, limit));
		if (s.tasks().isEmpty()) {
			return ToolResult.ok("Nothing to prep: there are no calendar meals from " + from + " to " + to + ".");
		}
		String saved = "";
		if (save) {
			LocalDate replace = AgentDates.parse(args.get("replace_date"), today, null);
			if (replace == null) {
				facade.savePrepDay(s);
			} else {
				facade.movePrepDay(replace, s);
			}
			saved = replace == null ? " SAVED on the calendar."
					: " SAVED: moved from " + AgentDates.show(replace) + ".";
		}
		StringBuilder sb = new StringBuilder("Prep session on " + AgentDates.show(prep) + " from " + start
				+ " for meals " + from + " to " + to + ": " + s.totalMinutes() + " min of " + limit + " available, "
				+ (s.fits() ? "fits" : "does NOT fit") + "." + (save ? saved : " (Preview only, nothing saved.)"));
		if (!s.leftOut().isEmpty()) {
			sb.append("\nLeft out to fit the time: ").append(String.join(", ", s.leftOut())).append(".");
		}
		sb.append("\nSteps:");
		for (Task t : s.ordered()) {
			sb.append("\n- ").append(start.plusMinutes(t.startMinute())).append(" (").append(t.minutes())
					.append(" min, ").append(t.lane()).append(") ").append(t.name());
		}
		List<Row> shorts = s.shortRows();
		if (!shorts.isEmpty()) {
			sb.append("\nMissing from the pantry:");
			for (Row r : shorts) {
				sb.append("\n- ").append(r.name()).append(": need ").append(Math.round(r.shortBy() * 10) / 10.0)
						.append(" ").append(r.unit());
			}
		}
		return ToolResult.ok(sb.toString());
	}
}