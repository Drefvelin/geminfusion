package me.Plugins.GemInfusion;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public class GemStat {
	private String id;
	private List<String> stats = new ArrayList<String>();
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public List<String> getStats() {
		return stats;
	}
	public void setStats(List<String> stats) {
		this.stats = stats;
	}
	public GemStat(String key, ConfigurationSection config) {
		this.id = key;
		this.stats = config.getStringList("stats");
	}
}
