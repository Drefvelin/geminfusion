package me.Plugins.GemInfusion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class InfusionMain extends JavaPlugin{
	public static InfusionMain plugin;
	public FileConfiguration config = getConfig();
	
	private ConfigLoader loader = new ConfigLoader();
	private InfusionEvents events = new InfusionEvents();
	private CommandManager commands = new CommandManager();
	@Override
	public void onEnable(){
		plugin = this;

		saveDefaultConfig();
		reloadConfig();
		config = getConfig();
		loader.loadConfig(config);

		getServer().getPluginManager().registerEvents(events, this);
		if (Bukkit.getPluginManager().getPlugin("TLibs") != null) {
			getServer().getPluginManager().registerEvents(new GemSocketRebuildListener(), this);
			getServer().getPluginManager().registerEvents(new GemUnsocketSnapshotListener(), this);
		}
		getCommand(commands.cmd1).setExecutor(commands);
	}
	public void reloadConfigCommand() {
		reloadConfig();
		config = getConfig();
		loader.loadConfig(config);
	}
	public void reloadConfigPCommand(Player p) {
		p.sendMessage(ChatColor.GREEN + "[GemInfusion]" + ChatColor.YELLOW + " Reloading plugin...");
		reloadConfigCommand();
		p.sendMessage(ChatColor.GREEN + "[GemInfusion]" + ChatColor.YELLOW + " Reloading complete!");
	}
}
