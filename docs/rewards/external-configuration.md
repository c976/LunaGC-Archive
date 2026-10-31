# Editable rewards

At first startup, AstaPS copies the packaged reward defaults into the working
directory's `rewards/` folder. This includes `loot.yaml`, `registry.tsv`,
and every bundled `.lcss` stylesheet.

Edit those external files. Run `reload rewards` in the server console or
`/reload rewards` as an authorized player to apply changes without rebuilding
the JAR or restarting the server. `reload all` also reloads rewards.

The entire reward configuration is compiled before activation. If syntax,
resource names, or loot definitions are invalid, reload reports an error and
keeps the previously active reward configuration. No edited files are replaced.
Restarting with invalid external rewards fails startup until corrected.

Bundled files are initial defaults only. Once `rewards/` exists, AstaPS
does not silently overwrite it when the JAR is updated. New packaged reward
files can be copied into the external directory explicitly.
