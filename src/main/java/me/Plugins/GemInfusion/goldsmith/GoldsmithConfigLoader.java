package me.Plugins.GemInfusion.goldsmith;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithConfigLoader {

	public void load(File configFile) {
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		GoldsmithCache.station = config.getString("station");
		GoldsmithCache.brandingTool = config.getString("branding-tool");
		GoldsmithCache.permission = config.getString("permission");
		if (GoldsmithCache.permission != null && GoldsmithCache.permission.isBlank()) {
			GoldsmithCache.permission = null;
		}
	}
}
