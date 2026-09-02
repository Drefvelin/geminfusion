package me.Plugins.GemInfusion.goldsmith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.GemInfusion.InfusionMain;
import me.Plugins.TLibs.TLibs;

public class GoldsmithInventoryManager {

	public static final String TITLE = "§8Goldsmithing";

	public static NamespacedKey projectKey() {
		return new NamespacedKey(InfusionMain.plugin, "gi_project");
	}

	public void openMenu(Player player) {
		Inventory inv = InfusionMain.plugin.getServer().createInventory(null, 27, TITLE);
		int slot = 0;
		for (JewelryProject project : JewelryProjectLoader.get().values()) {
			ItemStack icon = iconFromPath(project.getItem(), "project " + project.getId());
			if (icon == null) continue;
			if (slot >= inv.getSize()) break;
			inv.setItem(slot, decorate(icon, project));
			slot++;
		}
		fillEmpty(inv);
		player.openInventory(inv);
	}

	private ItemStack decorate(ItemStack icon, JewelryProject project) {
		ItemMeta meta = icon.getItemMeta();
		if (meta == null) return icon;
		meta.setDisplayName(project.getName());
		List<String> lore = new ArrayList<>();
		for (Map.Entry<String, Integer> e : project.getMaterialsByType().entrySet()) {
			lore.add("§7Requires §a" + e.getValue() + " " + GoldsmithMaterialTypeLoader.display(e.getKey()));
		}
		if (project.requiresGem()) {
			lore.add("§7Requires §a1 §7gem");
		}
		lore.add(" ");
		for (Map.Entry<GoldsmithHitType, Integer> e : project.getHitsByType().entrySet()) {
			lore.add("§7Requires §a" + e.getValue() + " " + e.getKey().getName() + " §7hits");
		}
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(projectKey(), PersistentDataType.STRING, project.getId());
		icon.setItemMeta(meta);
		return icon;
	}

	private ItemStack iconFromPath(String path, String label) {
		if (path == null || path.isBlank()) {
			GoldsmithLog.warn("Missing item path for " + label + ", skipping menu slot.");
			return null;
		}
		ItemStack stack = TLibs.getItemAPI().getCreator().getItemFromPath(path);
		if (stack == null) {
			GoldsmithLog.warn("Could not build item for " + label + " (" + path + "), skipping menu slot.");
			return null;
		}
		if (path.toLowerCase().startsWith("ia.") && stack.getType() == Material.DIRT) {
			GoldsmithLog.warn("Could not resolve ItemsAdder item for " + label + " (" + path + "), skipping menu slot.");
			return null;
		}
		ItemStack copy = stack.clone();
		copy.setAmount(1);
		return copy;
	}

	private void fillEmpty(Inventory inv) {
		ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		ItemMeta meta = fill.getItemMeta();
		meta.setDisplayName("§8 ");
		fill.setItemMeta(meta);
		for (int i = 0; i < inv.getSize(); i++) {
			if (inv.getItem(i) == null) {
				inv.setItem(i, fill.clone());
			}
		}
	}
}
