package com.iceyatom.surprisedrops;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global defaults and non-GUI options stored in {@code config/surprisedrops.toml} (FR-32, Section 6).
 *
 * <p>Only the small subset of TOML this file needs is supported: comments, booleans, integers,
 * quoted strings and (possibly multi-line) arrays of quoted strings.
 */
public final class SurpriseDropsConfig {
	public static final String FILE_NAME = "surprisedrops.toml";

	public static final List<String> DEFAULT_EXCLUDED = List.of(
			"minecraft:command_block",
			"minecraft:chain_command_block",
			"minecraft:repeating_command_block",
			"minecraft:command_block_minecart",
			"minecraft:structure_block",
			"minecraft:jigsaw",
			"minecraft:barrier",
			"minecraft:light",
			"minecraft:structure_void",
			"minecraft:debug_stick",
			"minecraft:knowledge_book"
	);

	public boolean enabledDefault = true;
	public int multiplierMin = 1;
	public int multiplierMax = 1;
	public boolean affectBlocks = true;
	public boolean affectMobs = true;
	public boolean affectContainers = true;
	public boolean affectOther = true;
	public int maxUnstackableCount = 64;
	public Set<String> excludedItems = new LinkedHashSet<>(DEFAULT_EXCLUDED);
	public boolean survivalItemsOnly = true;
	public String openConfigKeybind = "unbound";
	public boolean debugLogging = false;

	private static volatile SurpriseDropsConfig instance = new SurpriseDropsConfig();

	public static SurpriseDropsConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Loads the file, creating it with defaults if it does not exist. Invalid values fall back to defaults. */
	public static synchronized SurpriseDropsConfig load() {
		Path path = path();
		SurpriseDropsConfig config = new SurpriseDropsConfig();
		if (Files.exists(path)) {
			try {
				config.apply(parse(Files.readString(path, StandardCharsets.UTF_8)));
			} catch (IOException | RuntimeException e) {
				SurpriseDrops.LOGGER.warn("Could not read {}, using defaults: {}", path, e.toString());
			}
		} else {
			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, config.toToml(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				SurpriseDrops.LOGGER.warn("Could not write default {}: {}", path, e.toString());
			}
		}
		config.sanitize();
		instance = config;
		return config;
	}

	private void apply(Map<String, Object> values) {
		enabledDefault = bool(values, "enabled_default", enabledDefault);
		multiplierMin = integer(values, "multiplier_min", multiplierMin);
		multiplierMax = integer(values, "multiplier_max", multiplierMax);
		affectBlocks = bool(values, "affect_blocks", affectBlocks);
		affectMobs = bool(values, "affect_mobs", affectMobs);
		affectContainers = bool(values, "affect_containers", affectContainers);
		affectOther = bool(values, "affect_other", affectOther);
		maxUnstackableCount = integer(values, "max_unstackable_count", maxUnstackableCount);
		if (values.get("excluded_items") instanceof List<?> list) {
			excludedItems = new LinkedHashSet<>();
			for (Object o : list) {
				excludedItems.add(String.valueOf(o).trim());
			}
		}
		survivalItemsOnly = bool(values, "survival_items_only", survivalItemsOnly);
		if (values.get("open_config_keybind") instanceof String s) {
			openConfigKeybind = s;
		}
		debugLogging = bool(values, "debug_logging", debugLogging);
	}

	private void sanitize() {
		if (!SurpriseDropsHelper.isValidRange(multiplierMin, multiplierMax)) {
			SurpriseDrops.LOGGER.warn("Invalid multiplier range {}-{} in {}, using 1-1", multiplierMin, multiplierMax, FILE_NAME);
			multiplierMin = 1;
			multiplierMax = 1;
		}
		if (maxUnstackableCount < SurpriseDropsHelper.UNSTACKABLE_CAP_MIN || maxUnstackableCount > SurpriseDropsHelper.UNSTACKABLE_CAP_MAX) {
			SurpriseDrops.LOGGER.warn("max_unstackable_count {} out of range 1-1000, using 64", maxUnstackableCount);
			maxUnstackableCount = 64;
		}
	}

	private static boolean bool(Map<String, Object> values, String key, boolean fallback) {
		return values.get(key) instanceof Boolean b ? b : fallback;
	}

	private static int integer(Map<String, Object> values, String key, int fallback) {
		return values.get(key) instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? l.intValue() : fallback;
	}

	public String toToml() {
		StringBuilder sb = new StringBuilder();
		sb.append("# PA9 Surprise Drops configuration.\n");
		sb.append("# These are defaults for NEW worlds plus options that have no GUI control.\n");
		sb.append("# Values changed in the in-game screen are stored per world and override these.\n");
		sb.append("# Run /surprisedrops reload to apply edits without restarting.\n\n");
		sb.append("# Whether Surprise Drops starts ON in a newly created world.\n");
		sb.append("enabled_default = ").append(enabledDefault).append("\n\n");
		sb.append("# Default multiplier range per drop event (1-10000, min <= max). 1 keeps the vanilla amount.\n");
		sb.append("multiplier_min = ").append(multiplierMin).append("\n");
		sb.append("multiplier_max = ").append(multiplierMax).append("\n\n");
		sb.append("# Which drop categories are randomized by default.\n");
		sb.append("affect_blocks = ").append(affectBlocks).append("\n");
		sb.append("affect_mobs = ").append(affectMobs).append("\n");
		sb.append("affect_containers = ").append(affectContainers).append("\n");
		sb.append("affect_other = ").append(affectOther).append("\n\n");
		sb.append("# Cap on the count for items that cannot stack (max stack size 1). Range 1-1000.\n");
		sb.append("max_unstackable_count = ").append(maxUnstackableCount).append("\n\n");
		sb.append("# Item IDs that can never be chosen as a replacement.\n");
		sb.append("excluded_items = [\n");
		for (String id : excludedItems) {
			sb.append("  \"").append(escape(id)).append("\",\n");
		}
		sb.append("]\n\n");
		sb.append("# Default for new worlds: only choose items a survival player can obtain (skips spawn eggs,\n");
		sb.append("# bedrock, spawners, infested blocks, petrified oak slab, test blocks and other creative-only\n");
		sb.append("# items). Change it per world in the screen; changing it there reshuffles the seed.\n");
		sb.append("survival_items_only = ").append(survivalItemsOnly).append("\n\n");
		sb.append("# Default key for opening the configuration screen, e.g. \"key.keyboard.o\", or \"unbound\".\n");
		sb.append("# Also rebindable in the vanilla Controls screen.\n");
		sb.append("open_config_keybind = \"").append(escape(openConfigKeybind)).append("\"\n\n");
		sb.append("# Write assignment, multiplier and discard diagnostics to the log. Development use only.\n");
		sb.append("debug_logging = ").append(debugLogging).append("\n");
		return sb.toString();
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	// --- Minimal TOML parsing -------------------------------------------------------------

	static Map<String, Object> parse(String text) {
		Map<String, Object> out = new LinkedHashMap<>();
		String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = stripComment(lines[i]).trim();
			if (line.isEmpty() || line.startsWith("[")) {
				continue; // blank line or table header (unused)
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String key = line.substring(0, eq).trim();
			String value = line.substring(eq + 1).trim();
			if (value.startsWith("[")) {
				StringBuilder arr = new StringBuilder(value);
				while (!closesArray(arr) && i + 1 < lines.length) {
					arr.append(' ').append(stripComment(lines[++i]).trim());
				}
				out.put(key, parseArray(arr.toString()));
			} else {
				out.put(key, parseScalar(value));
			}
		}
		return out;
	}

	private static boolean closesArray(CharSequence s) {
		boolean inString = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && inString) {
				i++;
			} else if (c == '"') {
				inString = !inString;
			} else if (c == ']' && !inString) {
				return true;
			}
		}
		return false;
	}

	private static String stripComment(String line) {
		boolean inString = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\\' && inString) {
				i++;
			} else if (c == '"') {
				inString = !inString;
			} else if (c == '#' && !inString) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	private static Object parseScalar(String value) {
		if (value.equals("true")) {
			return Boolean.TRUE;
		}
		if (value.equals("false")) {
			return Boolean.FALSE;
		}
		if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
			return unescape(value.substring(1, value.length() - 1));
		}
		try {
			return Long.parseLong(value.replace("_", ""));
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static List<Object> parseArray(String value) {
		List<Object> out = new ArrayList<>();
		int start = value.indexOf('[') + 1;
		StringBuilder current = new StringBuilder();
		boolean inString = false;
		for (int i = start; i < value.length(); i++) {
			char c = value.charAt(i);
			if (inString) {
				if (c == '\\' && i + 1 < value.length()) {
					current.append(value.charAt(++i));
				} else if (c == '"') {
					inString = false;
					out.add(current.toString());
					current.setLength(0);
				} else {
					current.append(c);
				}
			} else if (c == '"') {
				inString = true;
			} else if (c == ']') {
				break;
			}
		}
		return out;
	}

	private static String unescape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				sb.append(s.charAt(++i));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
