package me.Plugins.GemInfusion.goldsmith;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.GemInfusion.AttributeInfluence;
import me.Plugins.GemInfusion.ConfigLoader;
import me.Plugins.GemInfusion.GemRarityPdc;
import me.Plugins.GemInfusion.GemStat;
import me.Plugins.GemInfusion.Gemstone;
import me.Plugins.TLibs.TLibs;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;

public final class JewelryOutput {

	private JewelryOutput() {
	}

	public static ItemStack build(GoldsmithStation station, Player player) {
		JewelryProject project = station.getProject();
		ItemStack gemStack = station.getGem();
		if (project == null || gemStack == null) {
			GoldsmithLog.warn("Jewelry output missing project or gem.");
			return null;
		}

		StatRoll roll = readGemStat(gemStack);
		if (roll == null) {
			GoldsmithLog.warn("Could not read a rolled stat off the deposited gem for project " + project.getId() + ".");
			return null;
		}

		double delta = AttributeInfluence.jewelry.forPlayer(player);
		double amount = Math.floor(roll.value * (1.0 + delta) * 10000) / 10000;

		String path = project.getItem();
		ItemStack base = TLibs.getItemAPI().getCreator().getItemFromPath(path);
		if (base == null || (path != null && path.toLowerCase().startsWith("ia.") && base.getType() == Material.DIRT)) {
			GoldsmithLog.warn("Could not build output for project " + project.getId() + " (" + path + "). Station left intact.");
			return null;
		}

		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(base));
		if (!applyStat(mmo, roll.statId, amount)) {
			return null;
		}
		ItemStack out = mmo.newBuilder().build();
		if (out == null) {
			GoldsmithLog.warn("Could not build jewelry item for project " + project.getId() + ".");
			return null;
		}
		out.setAmount(1);
		return out;
	}

	private static StatRoll readGemStat(ItemStack gemStack) {
		NBTItem nbt = NBTItem.get(gemStack);
		if (!nbt.hasType()) return null;
		Gemstone gem = ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID"));
		LiveMMOItem mmo = new LiveMMOItem(nbt);

		if (gem != null) {
			String rarityId = GemRarityPdc.read(gemStack);
			if (rarityId != null) {
				for (GemStat block : gem.getStats()) {
					if (block.getId().equalsIgnoreCase(rarityId)) {
						StatRoll roll = readDouble(mmo, block.getStatId());
						if (roll != null) return roll;
					}
				}
			}
			for (GemStat block : gem.getStats()) {
				StatRoll roll = readDouble(mmo, block.getStatId());
				if (roll != null) return roll;
			}
		}
		return firstNumeric(mmo);
	}

	private static StatRoll firstNumeric(MMOItem mmo) {
		for (ItemStat<?, ?> stat : mmo.getStats()) {
			if (stat == null) continue;
			String id = stat.getId();
			if (id == null || id.equalsIgnoreCase("SUCCESS_RATE") || id.equalsIgnoreCase("SUCCESS-RATE")) {
				continue;
			}
			Object data = mmo.getData(stat);
			if (data instanceof DoubleData d) {
				return new StatRoll(id, d.getValue());
			}
		}
		return null;
	}

	private static StatRoll readDouble(MMOItem mmo, String statId) {
		if (statId == null || statId.isBlank()) return null;
		if (statId.equalsIgnoreCase("SUCCESS_RATE") || statId.equalsIgnoreCase("SUCCESS-RATE")) return null;
		ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(statId.toUpperCase());
		if (stat == null) return null;
		Object data = mmo.getData(stat);
		if (data instanceof DoubleData d) {
			return new StatRoll(statId, d.getValue());
		}
		return null;
	}

	private static boolean applyStat(MMOItem mmo, String statId, double value) {
		ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase());
		if (itemStat == null) {
			GoldsmithLog.warn("Unknown MMOItems stat '" + statId + "' on jewelry output.");
			return false;
		}
		zeroOriginal(mmo, itemStat);
		DoubleData data = new DoubleData(value);
		mmo.setData(itemStat, data);
		@SuppressWarnings("deprecation")
		StatHistory hist = StatHistory.from(mmo, itemStat);
		if (hist != null) {
			hist.registerExternalData(data);
			mmo.setStatHistory(itemStat, hist);
		}
		return true;
	}

	private static void zeroOriginal(MMOItem mmo, ItemStat<?, ?> itemStat) {
		StatHistory hist = StatHistory.from(mmo, itemStat);
		if (hist != null) {
			Object og = hist.getOriginalData();
			if (og instanceof DoubleData doubleOg) {
				doubleOg.setValue(0);
				mmo.setStatHistory(itemStat, hist);
			}
		}
		mmo.setData(itemStat, new DoubleData(0));
	}

	private static final class StatRoll {
		final String statId;
		final double value;

		StatRoll(String statId, double value) {
			this.statId = statId;
			this.value = value;
		}
	}
}
