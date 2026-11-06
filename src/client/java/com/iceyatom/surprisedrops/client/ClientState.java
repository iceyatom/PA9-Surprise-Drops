package com.iceyatom.surprisedrops.client;

import com.iceyatom.surprisedrops.DropSettings;

/** The last settings the server sent this client (PKT-04). Reset on disconnect. */
public final class ClientState {
	private static volatile DropSettings settings;
	private static volatile boolean canEdit;

	private ClientState() {
	}

	public static void update(DropSettings newSettings, boolean newCanEdit) {
		settings = newSettings;
		canEdit = newCanEdit;
	}

	public static void clear() {
		settings = null;
		canEdit = false;
	}

	/** @return the live settings, or {@code null} if the server has not sent any (no mod, or not in a world) */
	public static DropSettings settings() {
		return settings;
	}

	public static boolean canEdit() {
		return canEdit;
	}
}
