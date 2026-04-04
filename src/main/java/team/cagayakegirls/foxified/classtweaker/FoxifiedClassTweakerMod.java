package team.cagayakegirls.foxified.classtweaker;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FoxifiedClassTweakerMod.MOD_ID)
public class FoxifiedClassTweakerMod {
	public static final String MOD_ID = "foxified_classtweaker";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public FoxifiedClassTweakerMod() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		//LOGGER.info("Hello world!");
	}
}
