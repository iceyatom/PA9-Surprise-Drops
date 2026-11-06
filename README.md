# PA9 Surprise Drops

A **Fabric mod** for **Minecraft Java Edition 26.3** that replaces every item drop with a randomly
assigned item in a randomly rolled quantity. Each loot table (a block, a mob, a chest, a fishing
catch) is permanently assigned one replacement item. Every time that table pays out, the mod swaps
the result for that item, keeping the vanilla quantity and multiplying it by a fresh random value
between a configurable minimum and maximum. A multiplier of 1 keeps the normal vanilla amount.

Example: with a multiplier range of 1–1,000, breaking Stone might always drop Diamonds, but one
break yields 3 Diamonds and the next yields 740. A **Shuffle** button re-rolls every assignment at
once.

Built from `PA9 Surprise Drops SRS.pdf` (SRS v1.0). FR/NFR/PKT/TC IDs in the source refer to that
document.

## Requirements

| Item | Version |
|---|---|
| Minecraft | Java Edition 26.3 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.161.0+26.3 or later for Minecraft 26.3 (in `.minecraft/mods/`) |
| Java | 25 (for building) |
| Mod Menu | optional — adds a gear shortcut to the config screen |

## Install

1. Install Fabric Loader 0.19.5+ for Minecraft 26.3 via the Fabric installer.
2. Put **Fabric API 0.161.0+26.3** (or later for Minecraft 26.3) in `.minecraft/mods/`, or in the
   server's `mods/` folder.
3. Put **`surprise-drops - 26.3 - 1.0.0.jar`** (from `build/libs/`) in the same folder.
4. (Optional) add Mod Menu.
5. Launch the `fabric-loader-26.3` profile.

All drop logic runs on the server (including the built-in server in single-player). On a
dedicated server the mod **must be installed on the server**; it is optional on clients, which only
need it to open the configuration screen. Players without the mod can still join and see the
replaced drops normally.

## Use

- **How drops change** — while the mod is ON, whenever a loot table rolls items, the whole result
  is replaced by that table's assigned item. The base quantity is the total the vanilla roll
  produced (a Zombie dropping 2 Rotten Flesh has a base of 2), multiplied by a random whole number
  from your range, rolled fresh for every drop. If the vanilla roll produced nothing (a leaf block
  that drops no sapling), nothing is added, so vanilla drop chances and Fortune/Looting still decide
  *whether* something drops.
  The total is split into normal stacks (150 Diamonds → 64 + 64 + 22). Items that can't stack
  (tools, armor) are capped at `max_unstackable_count` (default 64) so a big roll can't flood the
  world with entities.
- **Survival items only** — by default only items a survival player can obtain are chosen. Spawn
  eggs, bedrock, spawners, trial spawners, vaults, infested blocks, petrified oak slab, player
  heads, farmland, dirt path, test blocks and other creative/command-only items never drop. Turn
  this off with `survival_items_only = false`.
- **Items that need data** — maps, potions and similar items are created the way vanilla makes
  them, so they work:
  - an explorer map (village, treasure, mansion, trial chambers and so on) points to the nearest
    matching structure. If none is in range (for example in the Nether), it drops as an Empty Map;
  - a Map is a fresh map of the area where the drop happened;
  - potions, splash potions, lingering potions and tipped arrows get a random brewable potion;
  - an Enchanted Book gets a random enchantment, and Suspicious Stew a random effect.

  All stacks from one drop share the same map, potion or enchantment.
- **Categories** — every loot table falls into one of four categories, each with its own toggle:
  **Blocks** (block drops), **Mobs** (entity drops), **Containers** (chest, barrel, vault and other
  container loot) and **Other** (fishing, archaeology, villager gifts, piglin bartering and
  everything else). A category that is OFF drops vanilla loot. Loot tables added by datapacks and
  other mods are classified the same way.
- **Turn it on and off** — use the master toggle in the config screen, or run
  `/surprisedrops enable` / `/surprisedrops disable`. A brief message above the hotbar confirms the
  change ("Surprise Drops: ON"). While OFF, all drops are exactly vanilla.
- **Open the config screen** — click the **Surprise Drops** button in the bottom-left corner of the
  pause menu, bind "Open Surprise Drops settings" in Options → Controls (unbound by default, or set
  `open_config_keybind` in the config), or use Mod Menu's gear. The screen has:
  - the **master toggle**;
  - **Minimum** and **Maximum multiplier** fields — whole numbers from 1 to 10,000, minimum ≤
    maximum. An invalid entry turns red and disables **Done** until fixed. Above 1,000 a warning
    appears that very large multipliers can create many item entities and hurt performance; you can
    still save;
  - one toggle per **category**;
  - the **world seed** field (see below) and the **Shuffle** button;
  - **Done** (applies and saves immediately) and **Cancel**.

  Only operators (permission level 2) or the single-player host can change settings. Everyone else
  sees the screen read-only.
- **Seeds and Shuffle** — assignments are not stored one by one. Each table's item is worked out
  from the world's Surprise Drops seed and the loot table ID, so the same seed always gives the
  same assignments, across restarts and between players. Type a number or any text into the seed
  field (text is turned into a number, like vanilla world seeds) to reproduce a known set of
  assignments; leave it blank to keep the current seed.
  **Shuffle** asks for confirmation, then picks a new random seed and shows it in chat (click it to
  copy). It only affects future drops: items already dropped and chests already generated don't
  change, and the multiplier range and toggles stay as they are.
  Adding or removing mods or datapacks that add items can change assignments, because the pool of
  possible items changes.
- **What is never changed** — only loot-table output is replaced. Player death drops, items you
  drop or throw, the contents of a broken chest, and experience orbs are never touched. Mob
  equipment and villager trades always stay vanilla. A drop that holds stored items, such as a
  filled shulker box, is left as-is so its contents aren't lost. Loot from commands such as `/loot`
  is an ordinary loot roll and *is* replaced.
- **Containers** — container loot is generated through the normal vanilla fill, so existing items
  are never overwritten. If a big roll needs more slots than the container has free, the extra
  items are discarded.
- **Commands** — all need permission level 2 (the single-player host may always use them):
  - `/surprisedrops enable` / `disable`
  - `/surprisedrops shuffle [seed]` — new random seed, or set a specific number/text seed
  - `/surprisedrops multiplier <min> <max>`
  - `/surprisedrops reload` — re-reads the config file without a restart
  - `/surprisedrops lookup <loot table>` — shows the assigned item, category and current range,
    e.g. `/surprisedrops lookup minecraft:blocks/stone`
- **Safety** — if replacing a drop ever throws an error, that roll falls back to vanilla, a warning
  is logged and operators are told in chat. After three failures in a row the mod switches itself
  off until the server restarts.

Works in survival, creative and adventure, in every difficulty, in single-player and on servers.

## Settings and the config file

Each world saves its own settings (on/off, seed, multiplier range and category toggles), so every
world keeps its own assignments. The first time the mod loads in a world it picks a random seed.

`config/surprisedrops.toml` holds the **defaults for new worlds** plus options with no button in
the screen. Edit it and run `/surprisedrops reload` to apply changes.

| Option | Default | Description |
|---|---|---|
| `enabled_default` | `true` | Whether Surprise Drops starts ON in a new world. |
| `multiplier_min` | `1` | Default minimum multiplier (1–10000, ≤ `multiplier_max`). |
| `multiplier_max` | `1` | Default maximum multiplier (1–10000). The default of 1 keeps every vanilla quantity until you raise it. |
| `affect_blocks` | `true` | Randomize block drops. |
| `affect_mobs` | `true` | Randomize mob drops. |
| `affect_containers` | `true` | Randomize chest, barrel, vault and other container loot. |
| `affect_other` | `true` | Randomize fishing, archaeology, gifts, bartering and other loot. |
| `max_unstackable_count` | `64` | Cap on the count for items that can't stack (1–1000). |
| `excluded_items` | technical items | Item IDs that can never be chosen: command blocks, structure blocks, jigsaw, barrier, light, structure void, debug stick, knowledge book. |
| `survival_items_only` | `true` | Only choose items a survival player can obtain (no spawn eggs, bedrock, spawners, infested blocks, test blocks, etc.). |
| `open_config_keybind` | `"unbound"` | Default key for the config screen, e.g. `"key.keyboard.o"`. Also rebindable in Controls. |
| `debug_logging` | `false` | Write each replacement (table, item, multiplier, stack count) to the log. Development use only. |

## Build from source

```bash
cd "Surprise Drops"
java -version        # must report 25
gradlew.bat build    # Windows (./gradlew build on macOS/Linux)
# Output: build/libs/surprise-drops - 26.3 - 1.0.0.jar
```

Minecraft 26.x ships unobfuscated, so the code uses official Mojang names and the build declares
no `mappings` dependency. Unit tests for the assignment, multiplier and stack-splitting logic
(`SurpriseDropsHelper`) run as part of `build`.

## License

MIT
