package com.iceyatom.surprisedrops.client;

import com.iceyatom.surprisedrops.DropCategory;
import com.iceyatom.surprisedrops.DropSettings;
import com.iceyatom.surprisedrops.network.SurpriseDropsPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only browser of every loot table and the item it is assigned to under the current seed.
 * The list comes from the server ({@link SurpriseDropsPayloads.RequestTables}); a search box
 * filters by loot table, item ID, item name or category.
 */
public class LootTableScreen extends Screen {
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_LABEL = 0xFFA0A0A0;
	private static final int COLOR_WARNING = 0xFFFFAA00;
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_WIDTH = 320;

	private final Screen parent;
	private final List<Row> allRows = new ArrayList<>();
	private EditBox search;
	private TableList list;
	private String query = "";
	private boolean loaded;
	private long seed;

	public LootTableScreen(Screen parent) {
		super(Component.translatable("surprisedrops.tables.title"));
		this.parent = parent;
		requestTables();
	}

	static boolean canRequest() {
		return ClientState.settings() != null && ClientPlayNetworking.canSend(SurpriseDropsPayloads.RequestTables.TYPE);
	}

	/** Ask the server for the current assignments. Also called when the seed or settings change. */
	public void requestTables() {
		if (canRequest()) {
			ClientPlayNetworking.send(new SurpriseDropsPayloads.RequestTables());
		}
	}

	@Override
	protected void init() {
		search = addRenderableWidget(new EditBox(font, width / 2 - 150, 22, 300, 20, Component.translatable("surprisedrops.tables.search")));
		search.setHint(Component.translatable("surprisedrops.tables.search_hint"));
		search.setMaxLength(100);
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			refilter();
		});

		list = addRenderableWidget(new TableList(minecraft, width, height - 58 - 36, 58));
		refilter();

		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
				.bounds(width / 2 - 75, height - 28, 150, 20).build());
		setInitialFocus(search);
	}

	/** Called when the server's {@link SurpriseDropsPayloads.TableList} arrives. */
	public void setData(SurpriseDropsPayloads.TableList data) {
		seed = data.seed();
		allRows.clear();
		for (SurpriseDropsPayloads.TableEntry entry : data.entries()) {
			allRows.add(new Row(entry));
		}
		loaded = true;
		refilter();
	}

	private void refilter() {
		if (list == null) {
			return;
		}
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Row> shown = new ArrayList<>();
		for (Row row : allRows) {
			if (q.isEmpty() || row.searchText.contains(q)) {
				shown.add(row);
			}
		}
		list.replaceEntries(shown);
		list.setScrollAmount(0);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int cx = width / 2;
		graphics.centeredText(font, title, cx, 8, COLOR_TEXT);
		Component status;
		int color = COLOR_LABEL;
		if (!canRequest()) {
			status = Component.translatable("surprisedrops.tables.no_server");
			color = COLOR_WARNING;
		} else if (!loaded) {
			status = Component.translatable("surprisedrops.tables.loading");
		} else {
			status = Component.translatable("surprisedrops.tables.status", list.children().size(), allRows.size(), Long.toString(seed));
		}
		graphics.centeredText(font, status, cx, 46, color);
	}

	private static String shortId(String id) {
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}

	private class TableList extends ObjectSelectionList<Row> {
		TableList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}
	}

	private class Row extends ObjectSelectionList.Entry<Row> {
		private final String table;
		private final ItemStack stack;
		private final Component itemName;
		private final DropCategory category;
		private final String searchText;

		Row(SurpriseDropsPayloads.TableEntry entry) {
			table = shortId(entry.table());
			Identifier itemId = entry.item().isEmpty() ? null : Identifier.tryParse(entry.item());
			stack = itemId == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(itemId));
			itemName = stack.isEmpty() ? Component.translatable("surprisedrops.tables.no_item") : stack.getHoverName();
			DropCategory[] categories = DropCategory.values();
			category = entry.category() >= 0 && entry.category() < categories.length ? categories[entry.category()] : DropCategory.OTHER;
			searchText = (entry.table() + " " + entry.item() + " " + itemName.getString() + " " + categoryName().getString())
					.toLowerCase(Locale.ROOT);
		}

		private Component categoryName() {
			return Component.translatable("surprisedrops.screen." + category.name().toLowerCase(Locale.ROOT));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX();
			int y = getContentY();
			int right = getContentRight();
			if (!stack.isEmpty() && !stack.is(Items.AIR)) {
				graphics.item(stack, x, y + 2);
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 2 && mouseY < y + 18) {
					graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
				}
			}
			graphics.text(font, itemName, x + 22, y + 1, COLOR_TEXT);
			graphics.text(font, Component.literal(table), x + 22, y + 11, COLOR_LABEL);

			DropSettings settings = ClientState.settings();
			boolean off = settings != null && (!settings.enabled() || !settings.affects(category));
			Component label = off
					? Component.translatable("surprisedrops.tables.category_off", categoryName())
					: categoryName();
			graphics.text(font, label, right - font.width(label), y + 1, off ? COLOR_WARNING : COLOR_LABEL);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("surprisedrops.tables.narration", table, itemName);
		}
	}
}
