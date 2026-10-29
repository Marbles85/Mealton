package com.mealton.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads settings (LLM provider, model, API key) from a local ".env" file or
 * from environment variables. The real key lives ONLY in the local .env file,
 * which is listed in .gitignore, so it is never committed. The key is never
 * printed.
 */
public final class AppConfig {

	public static final String DEFAULT_PROVIDER = "mock"; // works with no key at all
	public static final String DEFAULT_MODEL = "claude-haiku-5-5";

	private static final String PLACEHOLDER = "YOUR_LOCAL_API_KEY_HERE";

	private final Map<String, String> values;

	public AppConfig(Map<String, String> values) {
		this.values = new HashMap<>(values);
	}

	/** Loads ".env" from the folder the program runs in. A missing file is fine. */
	public static AppConfig load() {
		return load(Path.of(".env"));
	}

	/** Loads the given file, then lets real environment variables override it. */
	public static AppConfig load(Path file) {
		Map<String, String> map = new HashMap<>();
		if (Files.isRegularFile(file)) {
			try {
				map.putAll(parse(Files.readAllLines(file)));
			} catch (IOException e) {
				throw new IllegalStateException("Could not read " + file + ": " + e.getMessage());
			}
		}
		for (String key : List.of("LLM_PROVIDER", "LLM_MODEL", "LLM_API_KEY")) {
			String fromEnv = System.getenv(key);
			if (fromEnv != null && !fromEnv.isBlank()) {
				map.put(key, fromEnv.trim());
			}
		}
		return new AppConfig(map);
	}

	/** Turns lines like KEY=value into a map. Skips blank lines and # comments. */
	static Map<String, String> parse(List<String> lines) {
		Map<String, String> map = new HashMap<>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue; // not a KEY=value line
			}
			String key = line.substring(0, eq).trim();
			String value = line.substring(eq + 1).trim();
			if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
					|| value.startsWith("'") && value.endsWith("'"))) {
				value = value.substring(1, value.length() - 1);
			}
			map.put(key, value);
		}
		return map;
	}

	public String get(String key, String defaultValue) {
		String v = values.get(key);
		return v == null || v.isBlank() ? defaultValue : v;
	}

	public String llmProvider() {
		return get("LLM_PROVIDER", DEFAULT_PROVIDER).toLowerCase();
	}

	public String llmModel() {
		return get("LLM_MODEL", DEFAULT_MODEL);
	}

	/** The secret key, or null if none was set. Never print or log this. */
	public String llmApiKey() {
		String key = get("LLM_API_KEY", null);
		return PLACEHOLDER.equals(key) ? null : key; // the .env.example placeholder is not a real key
	}

	public boolean hasApiKey() {
		return llmApiKey() != null;
	}

	/** Safe to print: shows whether a key exists, never the key itself. */
	@Override
	public String toString() {
		return "AppConfig[provider=" + llmProvider() + ", model=" + llmModel() + ", apiKey="
				+ (hasApiKey() ? "set" : "not set") + "]";
	}
}