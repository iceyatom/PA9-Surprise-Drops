package com.iceyatom.surprisedrops.client;

import com.iceyatom.surprisedrops.DropSettings;
import com.iceyatom.surprisedrops.SurpriseDropsConfig;
import com.iceyatom.surprisedrops.SurpriseDropsHelper;
import com.iceyatom.surprisedrops.network.SurpriseDropsPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The configuration screen (Section 7.4, FR-06 to FR-12). Edits a local copy of the settings,
 * validates it, and sends it to the server on Done. Read-only for players who may not edit.
 */
public class SurpriseDropsConfigScreen extends Screen {
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_LABEL = 0xFFA0A0A0;
	private static final int COLOR_ERROR = 0xFFFF5555;
	private static final int COLOR_WARNING = 0xFFFFAA00;
	private static final int COLOR_FIELD = 0xFFE0E0E0;

	private final Screen parent;

	// Working copy; survives re-init (resize, returning from the Shuffle confirmation).
	private boolean enabled;
	private boolean affectBlocks;
	private boolean affectMobs;
	private boolean affectContainers;
	private boolean affectOther;
	private String minText;
	private String maxText;
	private String seedText;

	private EditBox minBox;
	private EditBox maxBox;
	private EditBox seedBox;
	private Button doneButton;
	private Component validationMessage;
	private int validationColor;

	public SurpriseDropsConfigScreen(Screen parent) {
		super(Component.translatable("surprisedrops.screen.title"));
		this.parent = parent;
		DropSettings live = ClientState.settings();
		if (live == null) {
			// Not connected to a server with the mod: show the TOML defaults, read-only.
			SurpriseDropsConfig config = SurpriseDropsConfig.get();
			live = DropSettings.fromDefaults(config, 0L);
			seedText = "";
		} else {
			seedText = Long.toString(live.seed());
		}
		enabled = live.enabled();
		affectBlocks = live.affectBlocks();
		affectMobs = live.affectMobs();
		affectContainers = live.affectContainers();
		affectOther = live.affectOther();
		minText = Integer.toString(live.multiplierMin());
		maxText = Integer.toString(live.multiplierMax());
	}

	/** FR-12: only operators / the singleplayer host on a server running the mod may apply changes. */
	private boolean editable() {
		return ClientState.settings() != null && ClientState.canEdit()
				&& ClientPlayNetworking.canSend(SurpriseDropsPayloads.ApplySettings.TYPE);
	}

	@Override
	protected void init() {
		boolean editable = editable();
		int cx = width / 2;
		int left = cx - 155;
		int right = cx + 5;

		CycleButton<Boolean> master = addRenderableWidget(CycleButton.onOffBuilder(enabled)
				.create(left, 32, 310, 20, Component.translatable("surprisedrops.screen.master"), (b, v) -> enabled = v));
		master.active = editable;

		minBox = addRenderableWidget(new EditBox(font, left, 72, 150, 20, Component.translatable("surprisedrops.screen.min")));
		maxBox = addRenderableWidget(new EditBox(font, right, 72, 150, 20, Component.translatable("surprisedrops.screen.max")));
		for (EditBox box : new EditBox[]{minBox, maxBox}) {
			box.setMaxLength(6);
			box.setEditable(editable);
		}
		minBox.setValue(minText);
		maxBox.setValue(maxText);
		minBox.setResponder(s -> {
			minText = s;
			validate();
		});
		maxBox.setResponder(s -> {
			maxText = s;
			validate();
		});

		addCategory(left, 112, "surprisedrops.screen.blocks", affectBlocks, v -> affectBlocks = v, editable);
		addCategory(right, 112, "surprisedrops.screen.mobs", affectMobs, v -> affectMobs = v, editable);
		addCategory(left, 136, "surprisedrops.screen.containers", affectContainers, v -> affectContainers = v, editable);
		addCategory(right, 136, "surprisedrops.screen.other", affectOther, v -> affectOther = v, editable);

		seedBox = addRenderableWidget(new EditBox(font, left, 176, 230, 20, Component.translatable("surprisedrops.screen.seed")));
		seedBox.setMaxLength(256);
		seedBox.setValue(seedText);
		seedBox.setEditable(editable);
		seedBox.setHint(Component.translatable("surprisedrops.screen.seed_hint"));
		seedBox.setResponder(s -> seedText = s);

		Button shuffle = addRenderableWidget(Button.builder(Component.translatable("surprisedrops.screen.shuffle"), b -> confirmShuffle())
				.bounds(left + 235, 176, 75, 20).build());
		shuffle.active = editable;

		doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
				.bounds(left, height - 28, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(right, height - 28, 150, 20).build());

		validate();
	}

	private void addCategory(int x, int y, String key, boolean value, java.util.function.Consumer<Boolean> setter, boolean editable) {
		CycleButton<Boolean> button = addRenderableWidget(CycleButton.onOffBuilder(value)
				.create(x, y, 150, 20, Component.translatable(key), (b, v) -> setter.accept(v)));
		button.active = editable;
	}

	/** FR-08 / FR-09: flag invalid fields in red and disable Done; warn above 1,000. */
	private void validate() {
		Integer min = SurpriseDropsHelper.parseWholeNumber(minText);
		Integer max = SurpriseDropsHelper.parseWholeNumber(maxText);
		boolean minOk = min != null && min >= SurpriseDropsHelper.MULTIPLIER_MIN_LIMIT && min <= SurpriseDropsHelper.MULTIPLIER_MAX_LIMIT;
		boolean maxOk = max != null && max >= SurpriseDropsHelper.MULTIPLIER_MIN_LIMIT && max <= SurpriseDropsHelper.MULTIPLIER_MAX_LIMIT;
		boolean orderOk = minOk && maxOk && min <= max;
		minBox.setTextColor(minOk && orderOk ? COLOR_FIELD : COLOR_ERROR);
		maxBox.setTextColor(maxOk && orderOk ? COLOR_FIELD : COLOR_ERROR);

		if (!minOk || !maxOk) {
			validationMessage = Component.translatable("surprisedrops.screen.error_range");
			validationColor = COLOR_ERROR;
		} else if (!orderOk) {
			validationMessage = Component.translatable("surprisedrops.screen.error_order");
			validationColor = COLOR_ERROR;
		} else if (max > SurpriseDropsHelper.MULTIPLIER_WARNING_THRESHOLD) {
			validationMessage = Component.translatable("surprisedrops.screen.warning_large");
			validationColor = COLOR_WARNING;
		} else {
			validationMessage = null;
		}
		if (doneButton != null) {
			doneButton.active = !editable() || orderOk;
		}
	}

	/** FR-10: Shuffle only happens after confirmation. */
	private void confirmShuffle() {
		minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed && editable()) {
				ClientPlayNetworking.send(new SurpriseDropsPayloads.Shuffle());
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable("surprisedrops.screen.shuffle_confirm_title"),
				Component.translatable("surprisedrops.screen.shuffle_confirm_body")));
	}

	/** FR-12 / PKT-01 */
	private void done() {
		if (editable()) {
			Integer min = SurpriseDropsHelper.parseWholeNumber(minText);
			Integer max = SurpriseDropsHelper.parseWholeNumber(maxText);
			if (min == null || max == null || !SurpriseDropsHelper.isValidRange(min, max)) {
				return;
			}
			ClientPlayNetworking.send(new SurpriseDropsPayloads.ApplySettings(enabled, min, max,
					affectBlocks, affectMobs, affectContainers, affectOther, seedText.trim()));
		}
		onClose();
	}

	/** Called when the server pushes new settings while this screen is open (e.g. after Shuffle). */
	public void onServerSync(DropSettings settings) {
		seedText = Long.toString(settings.seed());
		if (seedBox != null) {
			seedBox.setValue(seedText);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int cx = width / 2;
		int left = cx - 155;
		graphics.centeredText(font, title, cx, 14, COLOR_TEXT);
		graphics.text(font, Component.translatable("surprisedrops.screen.min"), left, 61, COLOR_LABEL);
		graphics.text(font, Component.translatable("surprisedrops.screen.max"), cx + 5, 61, COLOR_LABEL);
		graphics.text(font, Component.translatable("surprisedrops.screen.categories"), left, 101, COLOR_LABEL);
		graphics.text(font, Component.translatable("surprisedrops.screen.seed"), left, 165, COLOR_LABEL);
		if (validationMessage != null) {
			graphics.centeredText(font, validationMessage, cx, 204, validationColor);
		}
		if (!editable()) {
			Component notice = ClientState.settings() == null
					? Component.translatable("surprisedrops.screen.no_server")
					: Component.translatable("surprisedrops.screen.read_only");
			graphics.centeredText(font, notice, cx, 218, COLOR_WARNING);
		}
	}
}
