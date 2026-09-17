# CherryPicking

A client-only Fabric mod for Minecraft 26.2 with general quality-of-life additions for Hypixel
Skyblock.

It is display-only: it shows and suggests, and never moves, clicks, or mines for you.

## Settings

`/cherry` opens the settings screen. It is also behind ModMenu's Settings button, and in the shared
config hub that the skyblock-flipper mod draws, which lists every mod in this family on one screen.
None of those are required: the mod installed on its own is fully usable through `/cherry`.

Settings are saved to `config/cherrypicking.json`.

## Building

```bash
./gradlew build
```

The build installs the jar straight into `~/Library/Application Support/minecraft/mods/`.

## License

LGPL-3.0-only.
