# Contributing

Carves & Crafts is made by [Studio Deriva](https://discord.gg/pQPWr7vfHu), a Minecraft modding team of
developers and artists. Everything we make is free and open source — source included, no paywall,
no premium version. Read it, learn from it, fork it, build on it.

## Ideas

Tell us what you would want to play. We read everything. If your idea turns into a mod or a
feature, you get credited for it in the release notes.

A good suggestion answers five things:

```
Idea:             one line, the whole thing
What it does:     blocks, items, mechanics
Why it's worth building: what is missing without it
Version / loader: if it only makes sense on one
References:       links, screenshots, mods that come close
```

One idea per message, and check whether somebody already suggested it before posting it again.

## Bugs

Open a ticket on [our Discord](https://discord.gg/pQPWr7vfHu), with:

- the mod version and the Minecraft/Fabric versions, plus any other mod involved (Sodium, Iris, ...),
- what happened and what you expected,
- the relevant part of `logs/latest.log` (lines from `carves_and_crafts`),
- for anything visual, a screenshot, and the pumpkin model and canvas density.

## Pull requests

```
./gradlew build     # compiles and runs the tests
./gradlew test      # tests only
```

House style:

- Java 21, tabs, no wildcard imports.
- Comments say **why**, not what, and talk about the code.
- Everything player-facing goes through the language file (`assets/carves_and_crafts/lang/en_us.json`).
- The server decides: anything a client sends (designs, files, schematic actions) is validated again
  on the server, and tool costs are computed there.
- Pumpkin and bench models are edited in Blockbench (`zucche/`) and converted with
  `./gradlew convertPumpkinModels` / `./gradlew convertBenchModel`; the generated files are committed.

## Licence

By contributing you agree your work ships under the MIT licence, like the rest of the project.
Do not post work that is not yours to give away: somebody else's paid commission, leaked content,
or anything you are under an agreement to keep quiet.
