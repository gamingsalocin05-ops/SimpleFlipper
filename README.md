# Simple Flipper

A deliberately minimal, hands-free **Hypixel SkyBlock Bazaar flipper** for Fabric — hardcoded to a
single item (**Enchanted Hard Stone**), no GUI config, three keybinds. It keeps one competitive
top-of-book buy order alive, drains the filled goods to the Builder NPC, and re-places the order
whenever it gets outbid — automatically, until you stop it.

It's intentionally small: a tick-driven click engine, order/menu parsing, purse-aware order sizing,
snapshot-aligned price polling, and a tiny single-item controller.

> [!WARNING]
> **Use at your own risk.** Automating gameplay (macros / bots) is against the
> [Hypixel rules](https://hypixel.net/rules) and can get your account punished or banned. This
> project is shared for educational purposes only. You are solely responsible for how you use it.

## Features

- **Autonomous single-item flip** of Enchanted Hard Stone — places its own buy order, claims fills,
  sells to the Builder, repeats.
- **Outbid handling** (place-before-clear): when your order is outbid or matched, it places a fresh
  top-of-book order *first* (so there's never a gap catching fills), then drains and cancels the old
  one. Caps at 2 concurrent orders.
- **Purse-aware order sizing** so concurrent orders fit your balance.
- **Minimal text HUD** (top-left): state, order fill, claimable, cycles, sold, purse.
- **No GUI, no config file** — everything is baked in. Three keys do everything.

## Requirements

- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/) `>= 0.16.14`
- [Fabric API](https://modrinth.com/mod/fabric-api) `0.141.x` for 1.21.11
- Java **21**

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/simpleflipper-0.1.0.jar`.

## Install

Drop the built jar into your instance's `mods/` folder (alongside Fabric API). Launch Minecraft.

## Usage

1. Be on Hypixel SkyBlock with a way to open `/managebazaarorders` without moving (e.g. an active
   Booster Cookie).
2. Stand **facing the Builder NPC** (the one that buys items).
3. Make sure you have enough purse for a buy order.
4. Press the keys:

| Key | Action |
| --- | --- |
| `=` | **Start / Pause** toggle |
| `Delete` (or `Backspace`) | **Kill** — panic stop everything immediately |
| `-` | **Finalize** — claim everything, cancel the order, sell leftovers, then stop |

The click engine aborts safely if you take damage, move, or receive a direct message.

## Configuration

There is none by design — it's hardcoded (item, ~30k-unit orders, 95% purse budget, pacing). To
change anything, edit the constants in
[`config/Config.java`](src/main/java/dev/salocin/simpleflipper/config/Config.java) and the
`ITEM` field in
[`core/Flipper.java`](src/main/java/dev/salocin/simpleflipper/core/Flipper.java), then rebuild.

## License

[MIT](LICENSE).
