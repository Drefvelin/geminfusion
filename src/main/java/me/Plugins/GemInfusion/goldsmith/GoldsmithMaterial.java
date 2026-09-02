package me.Plugins.GemInfusion.goldsmith;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class GoldsmithMaterial {

	private final String id;
	private final String name;
	private final String path;
	private final String type;

	public GoldsmithMaterial(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name", key));
		this.path = config.getString("path");
		this.type = config.getString("type", "gold");
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public String getType() {
		return type;
	}
}
