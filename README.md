# Over Protected

A [NeoForge](https://neoforged.net/) mod for Minecraft 1.21.1 that adds **Armor Straps** — wearable items that let you equip multiple pieces of the same armor type in a single slot, combining all of their stats.

---

## How It Works

Armor Straps are worn in the normal helmet, chestplate, leggings, or boots slot. Right-click one to open its inventory, then fill it with armor pieces of the matching type. The strap adds up the armor, toughness, knockback resistance, and enchantments from every stored piece and applies the total to your character — while visually rendering the first stored piece's texture on your body.

---

## Tiers

| Tier | Slots | Type |
|------|------:|------|
| Leather | 2 | Helmet / Chestplate / Leggings / Boots |
| Iron | 4 | Helmet / Chestplate / Leggings / Boots |
| Golden | 6 | Helmet / Chestplate / Leggings / Boots |
| Diamond | 8 | Helmet / Chestplate / Leggings / Boots |
| Netherite | 16 | Helmet / Chestplate / Leggings / Boots |

Higher-tier straps hold more pieces and therefore can accumulate more total protection.

---

## Features

- **Stat combining** — Armor, toughness, and knockback resistance values are summed across all stored pieces.
- **Enchantment stacking** — Enchantment levels from all stored pieces are added together (no cap).
- **Apotheosis compatibility** — Gem socket bonuses and affix attributes injected by [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) are captured and forwarded to the strap.
- **Visual armor render** — Equipping a strap shows the texture and model of the first stored armor piece on your character.
- **Per-tier GUIs** — Each tier has its own interface sized to fit its slot count.
- **Instant stat update** — Closing the strap's GUI immediately applies the new totals to your character without requiring a re-equip.

---

## Requirements

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.x

---

## Installation

1. Download the latest JAR from the [Releases](../../releases) page.
2. Drop it into your `mods/` folder alongside NeoForge 1.21.1.
3. Launch the game.

---

## Building from Source

```bash
./gradlew jar
```

The output JAR will be in `build/libs/`.
