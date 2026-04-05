# FoxifiedClassTweaker
Provide Fabric's ClassTweaker support for FancyModLoader.

## How to use
### For modder

Like add NeoForge's AccessTransformer. 
Add the following to your `META-INF/neoforge.mods.toml` file:
```toml
[[foxified.classtweaker]]
file = "modid.classtweaker"
```
If you don't want players to download this library, you can simply include the `processor` package as a jar-in-jar within the mod.

Currently, only FancyModLoader 10.x is supported (only working with official mappings).

Please note that you must have ClassTweaker/AccessWidener (theoretically compatible with AccessWidener) files in the corresponding directory.
### For player

If a mod specifies that it depends on this mod, you can simply install this mod.
