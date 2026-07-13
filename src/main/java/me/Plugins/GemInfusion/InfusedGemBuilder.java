package me.Plugins.GemInfusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;

public final class InfusedGemBuilder {
	private InfusedGemBuilder() {
	}

	public static void rollStats(MMOItem mmo, Gemstone gem, GemRarity rarity, int infusionAmount) {
		for (GemStat statBlock : gem.getStats()) {
			if (!statBlock.getId().equalsIgnoreCase(rarity.getId())) {
				continue;
			}
			List<String> usedStats = new ArrayList<>();
			if (rarity.getMaxStats() == -1) {
				usedStats = statBlock.getStats();
			} else {
				if (statBlock.getStats().size() < rarity.getMinStats()) {
					usedStats = statBlock.getStats();
				} else {
					while (usedStats.size() < rarity.getMinStats()) {
						Collections.shuffle(statBlock.getStats());
						if (!usedStats.contains(statBlock.getStats().get(0))) {
							usedStats.add(statBlock.getStats().get(0));
						}
					}
				}
				for (String stat : statBlock.getStats()) {
					if (usedStats.size() < rarity.getMaxStats()) {
						if (Math.random() < 0.5) {
							if (!usedStats.contains(stat)) {
								usedStats.add(stat);
							}
						}
					}
				}
			}
			for (String statString : usedStats) {
				String statType = statString.split("\\(")[0];
				double minAmount = 10000 * Double.parseDouble(statString.split("\\(")[1].split("\\-")[0]);
				double maxAmount = 10000
						* Double.parseDouble(statString.split("\\(")[1].split("\\-")[1].replace(")", ""));
				double statAmount = Math.floor(Math.random() * (maxAmount - minAmount) + minAmount) / 10000;
				mmo.setData(MMOItems.plugin.getStats().get(statType.toUpperCase()), new DoubleData(statAmount));
			}
		}

		double maxChance = 60.0 - infusionAmount;
		if (maxChance < 0.0) {
			maxChance = 0.0;
		}
		mmo.setData(ItemStats.SUCCESS_RATE, new DoubleData(Math.floor(Math.random() * maxChance) + 40));
	}

	public static void applyCosmetics(MMOItem mmo, Gemstone gem, GemRarity rarity) {
		mmo.setData(ItemStats.DISPLAYED_TYPE, new StringData("Infused Gemstone"));

		String infusedName = rarity.getName() + " Infused " + gem.getName();
		StringData itemName = (StringData) mmo.getData(ItemStats.NAME);
		if (itemName == null) {
			itemName = new StringData(infusedName);
		} else {
			itemName.setString(infusedName);
		}
		mmo.replaceData(ItemStats.NAME, itemName);
		StatHistory hist = StatHistory.from(mmo, ItemStats.NAME);
		if (hist != null) {
			NameData og = (NameData) hist.getOriginalData();
			og.setString(infusedName);
			mmo.setStatHistory(ItemStats.NAME, hist);
		}

		mmo.setData(ItemStats.GEM_COLOR, new StringData(gem.getSocketColour()));

		List<String> loreList = new ArrayList<>();
		loreList.add(ChatColor.GRAY + "Gemstone Type: " + gem.getSocketNameColour() + gem.getSocketColour());
		loreList.add(ChatColor.GRAY + "Rarity: " + rarity.getName());
		mmo.setData(ItemStats.LORE, new StringListData(loreList));
	}

	public static ItemStack applyCosmeticsToItem(ItemStack item, Gemstone gem, GemRarity rarity) {
		if (item == null || item.getType().isAir() || gem == null || rarity == null) {
			return item;
		}
		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		applyCosmetics(mmo, gem, rarity);
		return finalizeItem(mmo.newBuilder().build(), rarity.getId());
	}

	public static ItemStack buildInfusedGem(Gemstone gem, GemRarity rarity, int infusionAmount) {
		ItemStack blankGem = gem.getMMOItem().newBuilder().build();
		MMOItem infusedGem = new LiveMMOItem(NBTItem.get(blankGem));
		rollStats(infusedGem, gem, rarity, infusionAmount);
		applyCosmetics(infusedGem, gem, rarity);
		return finalizeItem(infusedGem.newBuilder().build(), rarity.getId());
	}

	public static ItemStack finalizeItem(ItemStack item, String rarityId) {
		if (item == null) {
			return null;
		}
		GemRarityPdc.write(item, rarityId);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return item;
		}
		meta.addEnchant(Enchantment.DURABILITY, 1, true);
		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		item.setItemMeta(meta);
		return item;
	}
}
