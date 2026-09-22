# CherryPicking

A client-side Fabric mod for Minecraft 26.2 that adds quality-of-life features to Hypixel Skyblock.

## Settings

`/cherry` opens the settings screen. ModMenu's Settings button and the shared config hub from
skyblock-flipper open the same screen. Neither is required.

Settings are saved to `config/cherrypicking.json`.

## Building

Requires JDK 25.

```bash
./gradlew build
```

The build also copies the jar into `~/Library/Application Support/minecraft/mods/`.

## License

[LGPL-3.0](LICENSE).

The puzzle solution files in `src/main/resources/assets/cherrypicking/puzzles/` come from
[Odin](https://github.com/odtheking/Odin) unchanged: Copyright (c) 2025, odtheking — BSD 3-Clause.
