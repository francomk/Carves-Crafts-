# Carves & Crafts

Pumpkin carving for Fabric 1.21.11, by [Studio Deriva](https://discord.gg/pQPWr7vfHu).

Carves & Crafts adds a carving bench where you cut and paint pumpkins pixel by pixel. Cuts go all the
way through the wall, so you see the flesh and the hollow inside; paint takes any color; a torch or a
candle turns the finished pumpkin into a lantern that glows in the color of its flame.

**Free and open source**, like everything we make: MIT, source included, no premium version.

---

## Features

- **Nine pumpkin models**, each with its own shape and skin: Classic, White, Blue, Cinderella, Mini
  Yellow, Warty, Butternut, Kabocha and Turban. Warts, caps, stems and leaves stay as they are; the
  body is what you carve.
- **Real holes.** A cut pixel goes through the wall and shows the flesh, the hole's sides and the
  cavity behind it. Warts sitting over a cut disappear with it.
- **Paint in any color.** Full RGB with a hex field, HSV picker, dye swatches and recent colors.
  Fill, eyedropper, mirror, undo/redo, zoom and pan. One face at a time, on the four sides.
- **Three canvas densities** (x1, x2, x4): the canvas keeps the proportions of the face, with
  square pixels.
- **Lanterns.** Right-click a placed pumpkin with a torch, soul torch, copper torch, redstone torch or
  any candle. It gives the source's vanilla light level and glows inside in its color; break the
  pumpkin to get the source back.
- **Tools that wear out.** The carving knife loses one durability per cut pixel, the paintbrush one
  charge per painted pixel. Costs are paid on confirm, on the difference only; erasing paint is free.
  A Color Palette refills the brush. Everything is free in creative.
- **Schematics.** Save any design to your library, copy it onto a blank pumpkin of the same model
  (paying the tools in full), rename, delete. Pumpkins remember who carved them.
- **Share designs as files.** Export a schematic to a `.pumpkin` file, import files from friends,
  keep your own collection on your computer.
- **Presets.** Data packs can ship ready-made designs that players copy or save from the bench.
- **Farming and worldgen.** Three kinds of seeds — Field Pumpkin, Heirloom Pumpkin, Winter Squash —
  each growing a weighted mix of models. Wild patches spawn in matching biomes.
- **Pumpkins stay visible as far as you can see**, and beyond a configurable distance they are drawn
  without their design, which keeps big displays cheap.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | Fabric 0.19.5+ |
| Java | 21 |
| Required | Fabric API |
| Optional | Mod Menu (config screen), JEI (recipes), Jade or WTHIT (what you are looking at) |

Install it on **both the client and the server**. Singleplayer works out of the box.

## Getting started

| Item | Recipe |
|---|---|
| Carving Bench | three wooden slabs on top; plank, iron ingot, plank in the middle; two planks as legs |
| Carving Knife | iron ingot above a stick |
| Paintbrush | feather above a stick (crafted empty) |
| Color Palette | all 16 dyes and a plank, in the Palette tab of the carving bench |
| Seeds | one blank pumpkin of the variety gives 4 seeds |

1. Find a wild pumpkin patch, or grow one from seeds on farmland like a vanilla pumpkin.
2. Craft a carving bench. It is two blocks wide, like a bed, so it needs room for both halves.
3. Put the pumpkin, the knife and the paintbrush in the bench. Recharge the brush with a Color
   Palette using the **+** button.
4. Press **Carve**, pick a canvas density, and draw. **Confirm** applies the design and wears the
   tools; leaving without confirming asks first.

## Schematics, files and presets

The Schematics tab of the bench switches between three lists with the button next to the pumpkin
slot:

- **World** — your library in this world. Save, Copy, Rename, Delete, and **Export** to a file.
- **PC** — the `.pumpkin` files in `<game folder>/carves_and_crafts/schematics/`. **Import** adds a
  file to your world library, **Folder** opens the folder, Delete removes the file. To rename a file,
  rename it on disk; files added by hand appear in the list on their own.
- **Presets** — designs shipped by data packs. Save or Copy, like your own.

Files are validated on load and again by the server. Designs for a pumpkin model that isn't installed
are shown greyed out and refused on import.

## Configuration

`config/carves_and_crafts.json` (server, created on first start):

```jsonc
{
  "maxSchematicsPerPlayer": 0,     // 0 = no count limit (libraries are always capped at 4 MiB of designs)
  "schematicActionsPerMinute": 0,  // rate limit on save/rename/delete/copy/import, 0 = none
  "allowSchematicImport": true     // let players import .pumpkin files
}
```

`config/carves_and_crafts-client.json` (client):

```jsonc
{
  "designRenderDistance": 64       // blocks; farther pumpkins are drawn without their design
}
```

With Mod Menu installed, both are editable in game. Server options can be edited from the title
screen or in singleplayer; on a dedicated server they live in the server's file.

## Data packs

Everything below can be overridden or extended by a data pack:

| What | Where |
|---|---|
| Presets | `data/<namespace>/preset/<name>.pumpkin` — export a design from the bench and drop it in |
| Harvest weights | `data/carves_and_crafts/pumpkin_variety/<variety>.json`, e.g. `{"variants": [{"model": "classic_pumpkin", "weight": 70}]}` |
| Wild patch biomes | biome tags `#carves_and_crafts:grows_field_pumpkin`, `grows_heirloom_pumpkin`, `grows_winter_squash` |
| Wild patch shape | `worldgen/configured_feature/patch_<variety>.json` and `placed_feature/patch_<variety>.json` |

Varieties are `field_pumpkin`, `heirloom_pumpkin` and `winter_squash`.

## Commands

| Command | Who | What |
|---|---|---|
| `/dpumpkin <pos> init\|cut\|erase\|paint\|fill\|demo\|info` | operators | edit or inspect the design of a placed pumpkin directly |

## Compatibility

- **Mod Menu**: config screen.
- **JEI**: the Color Palette recipe (made in the bench, not the crafting table) and info pages for
  the bench, the tools, the pumpkins and the seeds.
- **Jade / WTHIT**: a placed pumpkin shows its canvas density, its carvers and what lights it.

Sodium and Iris are meant to work; tell us if something looks wrong with them.

## Building from source

```
./gradlew build          # jar in build/libs/
./gradlew test           # 64 unit tests: design encoding, editing rules, meshes, files, varieties
```

Java 21 or newer, and a first run with network access to fetch the dependencies.

The pumpkin and bench models live in `zucche/` as Blockbench files. After changing one, regenerate the
resources with `./gradlew convertPumpkinModels` or `./gradlew convertBenchModel` and commit the
generated files.

## Contributing

Ideas, bug reports and pull requests are all welcome — [our Discord](https://discord.gg/pQPWr7vfHu)
has a channel for each. If an idea of yours ships, your name goes in the credits and in the release
notes. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT. See [LICENSE](LICENSE). © 2026 Studio Deriva.
