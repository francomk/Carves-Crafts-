# Changelog

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
