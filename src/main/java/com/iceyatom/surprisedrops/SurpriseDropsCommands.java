package com.iceyatom.surprisedrops;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Locale;
import java.util.Optional;

/** {@code /surprisedrops} (FR-02, FR-34). */
public final class SurpriseDropsCommands {
	private SurpriseDropsCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("surprisedrops")
				.requires(SurpriseDropsCommands::allowed)
				.then(Commands.literal("enable").executes(ctx -> setEnabled(ctx, true)))
				.then(Commands.literal("disable").executes(ctx -> setEnabled(ctx, false)))
				.then(Commands.literal("shuffle")
						.executes(ctx -> shuffle(ctx, null))
						.then(Commands.argument("seed", StringArgumentType.greedyString())
								.executes(ctx -> shuffle(ctx, StringArgumentType.getString(ctx, "seed")))))
				.then(Commands.literal("multiplier")
						.then(Commands.argument("min", IntegerArgumentType.integer(SurpriseDropsHelper.MULTIPLIER_MIN_LIMIT, SurpriseDropsHelper.MULTIPLIER_MAX_LIMIT))
								.then(Commands.argument("max", IntegerArgumentType.integer(SurpriseDropsHelper.MULTIPLIER_MIN_LIMIT, SurpriseDropsHelper.MULTIPLIER_MAX_LIMIT))
										.executes(SurpriseDropsCommands::multiplier))))
				.then(Commands.literal("reload").executes(SurpriseDropsCommands::reload))
				.then(Commands.literal("lookup")
						.then(Commands.argument("loot_table", IdentifierArgument.id())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
										ctx.getSource().getServer().reloadableRegistries().lookup()
												.lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier),
										builder))
								.executes(SurpriseDropsCommands::lookup))));
	}

	/** Permission level 2 on a server; the singleplayer host may always use it (FR-02). */
	private static boolean allowed(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			return SurpriseDropsServer.canEdit(player);
		}
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
	}

	private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		CommandSourceStack source = ctx.getSource();
		SurpriseDropsServer.setEnabled(source.getServer(), source.getPlayer(), enabled);
		source.sendSuccess(() -> Component.translatable(enabled ? "surprisedrops.message.on" : "surprisedrops.message.off"), true);
		return 1;
	}

	private static int shuffle(CommandContext<CommandSourceStack> ctx, String seedText) {
		CommandSourceStack source = ctx.getSource();
		Long seed = SurpriseDropsHelper.parseSeed(seedText);
		long applied = SurpriseDropsServer.shuffle(source.getServer(), seed);
		source.sendSuccess(() -> SurpriseDropsServer.seedMessage("surprisedrops.message.shuffled", applied), true);
		return 1;
	}

	private static int multiplier(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		int min = IntegerArgumentType.getInteger(ctx, "min");
		int max = IntegerArgumentType.getInteger(ctx, "max");
		if (!SurpriseDropsHelper.isValidRange(min, max)) {
			source.sendFailure(Component.translatable("surprisedrops.command.invalid_range"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		WorldState state = WorldState.get(server);
		state.setSettings(state.settings().withRange(min, max));
		SurpriseDrops.onSettingsChanged(server);
		source.sendSuccess(() -> Component.translatable("surprisedrops.command.multiplier", min, max), true);
		if (max > SurpriseDropsHelper.MULTIPLIER_WARNING_THRESHOLD) {
			source.sendSuccess(() -> Component.translatable("surprisedrops.screen.warning_large").withStyle(ChatFormatting.YELLOW), false);
		}
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		SurpriseDropsConfig.load();
		LootReplacer.rebuildPool();
		source.sendSuccess(() -> Component.translatable("surprisedrops.command.reloaded", LootReplacer.pool().size()), true);
		return 1;
	}

	private static int lookup(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		Identifier id = IdentifierArgument.getId(ctx, "loot_table");
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
		Optional<?> exists = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).get(key);
		if (exists.isEmpty()) {
			source.sendFailure(Component.translatable("surprisedrops.command.unknown_table", id.toString()));
			return 0;
		}
		DropSettings settings = WorldState.get(server).settings();
		LootTable table = server.reloadableRegistries().getLootTable(key);
		DropCategory category = DropCategory.of(table.getParamSet());
		Item item = LootReplacer.assignedItem(settings.seed(), id);
		Component itemName = item == null ? Component.literal("-") : Component.literal(BuiltInRegistries.ITEM.getKey(item).toString()).withStyle(ChatFormatting.AQUA);
		String categoryName = category == null ? "none (always vanilla)" : category.name().toLowerCase(Locale.ROOT)
				+ (settings.affects(category) ? "" : " (category OFF)");
		source.sendSuccess(() -> Component.translatable("surprisedrops.command.lookup", id.toString(), itemName,
				settings.multiplierMin(), settings.multiplierMax(), categoryName,
				Component.translatable(settings.enabled() ? "options.on" : "options.off")), false);
		return 1;
	}
}
