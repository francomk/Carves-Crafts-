# Changelog

## 1.0.6 — security fixes

- A malformed design sent by a modified client, or read from a `.pumpkin` file or a data pack preset, is now
  rejected cleanly. Before, it threw an unexpected error: the server logged two stack traces per packet (a way
  to flood the logs), and a bad file in the schematics folder crashed the game whenever the PC view opened.
- Pumpkin designs inside items are now sent as references in every packet, including the item hover of a
  death message. Killing someone while holding a renamed bundle or shulker box full of large designs could
  disconnect every player online.
- Schematic libraries: each entry now also counts 1 KiB for its name and authors towards the 4 MiB cap, so a
  library can't grow to hundreds of thousands of near-empty entries. Existing libraries are kept.
- Carving bench: opening the bench you already have open no longer frees it for another player.

## 1.0.5 — interface fixes

- Editor: in narrow windows the knife and paint costs get their own row above the buttons, instead of
  running under "Save schematic".
- Bench, Schematics tab: the World / PC / Presets button is wider, so "Presets" fits instead of
  scrolling. The list next to it is slightly narrower.
- Mod Menu: the mod has its real icon, a link to the source code, and bug reports point to a ticket on
  the Studio Deriva Discord.

## 1.0.4 — inventory ban fix

- Carved pumpkin items no longer send their design to clients: only an 18-byte reference to it. An
  inventory, a chest or a shulker box full of large designs can no longer disconnect players. Pumpkins
  inside block entities (shelves, campfires...) are sent the same way.
- The bench sends the design of the pumpkin it holds, so the editor works as before.
- Creative inventory: items sent back by the client get their design back from the server. An item whose
  design the server no longer knows (e.g. from a saved hotbar, much later) is refused.
- New server option `maxColorsPerDesign` and admin command `/carves maxcolors [off|1-254]`, off by
  default: caps the colors of new designs (carved, imported or copied). Existing pumpkins and schematics
  are kept; a pumpkin already over the limit can be edited without adding colors. The editor shows a
  color counter and blocks confirming over the limit.

## 1.0.3 — security fixes

- Placed pumpkins now send only their design's hash in chunk data; clients fetch the designs they draw
  from the server, cached by hash. Areas full of large designs can no longer make chunk packets too big.
- The server caps how many design bytes it sends to each player on request (library pages and placed
  pumpkins). A rejected library action no longer sends a page back.
- Schematic libraries are capped at 4 MiB of designs, whatever the config says.
- Author names in imported files are validated: an empty name could make a player's data unsavable.
  Authors that come from a file are shown as "(names from a file)" in tooltips.
- Saving a schematic while confirming a carving now counts towards the library rate limit.
- Preset actions check the preset's design, so a data pack reload can't swap the preset you picked.
- Client render cache: no more texture mix-ups or rebuild loops with more than 512 designs on screen;
  textures are rebuilt after a resource pack change.
- A pumpkin placed from an item that already holds a light source now lights up.

The full account of the audit behind these fixes, including what has not been verified, is in
`HOW_WE_VERIFIED_AND_FIXED_THE_BUGS.txt`.

## 1.0.0 — first release

- Carving bench, two blocks wide: carve and paint pumpkins one face at a time, with real holes that
  show the flesh and the cavity, full RGB paint, fill, eyedropper, mirror and undo/redo.
- Nine pumpkin models with their own shapes and skins; canvas densities x1, x2 and x4.
- Carving knife and paintbrush that wear out per pixel, paid on confirm; Color Palette crafted in the
  bench to refill the brush. Free in creative.
- Lanterns: torches, soul torches, copper torches, redstone torches and candles light a pumpkin from
  inside, tinted by the source.
- Schematic library per player, copies onto blank pumpkins, carvers shown in the tooltip.
- `.pumpkin` files: export, import, a local folder of designs, and presets from data packs.
- Three seed varieties with weighted harvests, and wild patches in matching biomes, all data-driven.
- Configurable design render distance; pumpkins stay visible up to the game's render distance.
- Optional Mod Menu config screen, JEI recipes and info pages, Jade and WTHIT tooltips.
