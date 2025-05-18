package me.Plugins.GemInfusion;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandManager implements Listener, CommandExecutor{
	public String cmd1 = "geminfusion";
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getName().equalsIgnoreCase(cmd1)) {
			if(Permissions.isAdmin(sender) == false) {
				sender.sendMessage("§cYou do not have access to this command!");
				return false;
			}
			if(args[0].equalsIgnoreCase("reload")) {
				if(sender instanceof Player) {
					Player p = (Player) sender;
					JavaPlugin.getPlugin(InfusionMain.class).reloadConfigPCommand(p);
				} else {
					JavaPlugin.getPlugin(InfusionMain.class).reloadConfigCommand();
				}
				return true;
			}
		}
		return false;
	}
}
