# FoxifiedClassTweaker
Provide [Fabric's ClassTweaker](https://docs.fabricmc.net/develop/class-tweakers/) support for FancyModLoader.

## How to use

### For modder

> [!IMPORTANT]
> When using ClassTweaker or AccessWidener in a development environment, please use Loom whenever possible (e.g., architectury-loom, RelativityMC's neo-loom, etc.).

Like add NeoForge's AccessTransformer. 
Add the following to your `META-INF/neoforge.mods.toml` file:

> [!IMPORTANT]
> You must place the ClassTweaker/AccessWidener files (which are theoretically compatible with AccessWidener) in the appropriate directory.

```toml
[[foxified.classtweaker]]
file = "modid.classtweaker"
```
If you don't want players to download this library, you can simply include the `processor` package as a jar-in-jar within the mod.

Currently, only FancyModLoader 10.x is supported (only working with official mappings).

Please note that you must have ClassTweaker/AccessWidener (theoretically compatible with AccessWidener) files in the corresponding directory.

### For player

If a mod specifies that it depends on this mod, you can simply install this mod.
