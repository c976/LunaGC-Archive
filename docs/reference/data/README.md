# Historical data examples (not runtime inputs)

These files were formerly stored at the root of `data/`, even though no
server source file reads their names. The original bytes are preserved here
for research or future manual integration:

- `GachaBannerPrefab.txt`: a banner-name/reference list.
- `ShopAloyExchange.json`: a sample shop dataset.
- `ShopGiftAscension.json`: a sample shop dataset.
- `ShopStarglowMaterials66.json`: a sample shop dataset.

AstaPS reads `data/Shop.json` for runtime shop configuration; it does not
automatically merge these extra examples. If one is needed in-game, integrate
it into the actual supported shop data rather than restoring unused files to
the `data/` root.
