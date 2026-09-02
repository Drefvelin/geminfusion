package me.Plugins.GemInfusion.goldsmith;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.GemInfusion.ConfigLoader;
import me.Plugins.GemInfusion.Gemstone;
import me.Plugins.GemInfusion.Permissions;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.Utils.IntCounter;

public class GoldsmithStationManager implements Listener {

	private static final long CLICK_COOLDOWN_MS = 200L;

	private final HashMap<Location, GoldsmithStation> stations = new HashMap<>();
	private final HashMap<UUID, Long> clickCooldown = new HashMap<>();
	private final HashMap<UUID, GoldsmithStation> openMenu = new HashMap<>();
	private final GoldsmithInventoryManager menus = new GoldsmithInventoryManager();
	private boolean dirty;

	public GoldsmithStation get(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		return stations.get(key);
	}

	public GoldsmithStation getOrCreate(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		GoldsmithStation station = stations.get(key);
		if (station == null) {
			station = new GoldsmithStation(key);
			stations.put(key, station);
		}
		return station;
	}

	public GoldsmithStation remove(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		GoldsmithStation removed = stations.remove(key);
		if (removed != null) {
			openMenu.entrySet().removeIf(e -> e.getValue() == removed);
			GoldsmithStationStore.delete(key);
			markDirty();
		}
		return removed;
	}

	public void put(GoldsmithStation station) {
		if (station == null || station.getLoc() == null) return;
		stations.put(key(station.getLoc()), station);
	}

	public Collection<GoldsmithStation> getStations() {
		return stations.values();
	}

	public void clear() {
		stations.clear();
		clickCooldown.clear();
		openMenu.clear();
		dirty = false;
	}

	public void markDirty() {
		dirty = true;
	}

	/** Restores tables from disk into this manager. Call after configs are loaded. */
	public void loadPersisted() {
		for (GoldsmithStation station : GoldsmithStationStore.loadAll()) {
			put(station);
		}
		dirty = false;
	}

	/**
	 * Writes live in-progress tables and drops leftover files.
	 * @param force write even when nothing changed
	 */
	public void flush(boolean force) {
		if (!force && !dirty) return;
		GoldsmithStationStore.saveAll(stations.values());
		dirty = false;
	}

	public boolean isGoldsmithStation(Block block) {
		if (block == null || GoldsmithCache.station == null) return false;
		return TLibs.getBlockAPI().getChecker().checkBlock(block, GoldsmithCache.station);
	}

	public static Location key(Location loc) {
		if (loc == null) return null;
		if (loc.getWorld() == null) {
			return new Location(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		}
		return loc.getBlock().getLocation();
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getClickedBlock() == null) return;
		Action action = e.getAction();
		if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
		if (!isGoldsmithStation(e.getClickedBlock())) return;

		Player player = e.getPlayer();
		if (action == Action.RIGHT_CLICK_BLOCK) {
			e.setCancelled(true);
			if (!Permissions.canUseGoldsmith(player)) {
				if (!onCooldown(player)) {
					player.sendMessage("§cYou do not have permission to use goldsmithing.");
					markCooldown(player);
				}
				return;
			}
			if (onCooldown(player)) return;
			handleRightClick(e);
			return;
		}

		if (!Permissions.canUseGoldsmith(player)) {
			return;
		}
		if (onCooldown(player)) return;
		if (handleLeftClick(e)) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onMenuClick(InventoryClickEvent e) {
		if (!e.getView().getTitle().equals(GoldsmithInventoryManager.TITLE)) return;
		e.setCancelled(true);
		if (!(e.getWhoClicked() instanceof Player p)) return;
		if (!Permissions.canUseGoldsmith(p)) {
			p.sendMessage("§cYou do not have permission to use goldsmithing.");
			p.closeInventory();
			return;
		}
		ItemStack clicked = e.getCurrentItem();
		if (clicked == null || !clicked.hasItemMeta()) return;
		ItemMeta meta = clicked.getItemMeta();
		String projectId = meta.getPersistentDataContainer().get(GoldsmithInventoryManager.projectKey(), PersistentDataType.STRING);
		if (projectId == null) return;

		JewelryProject project = JewelryProjectLoader.getByString(projectId);
		if (project == null) return;

		GoldsmithStation station = openMenu.get(p.getUniqueId());
		if (station == null) {
			p.sendMessage("§cThat bench is no longer available.");
			p.closeInventory();
			return;
		}
		if (station.hasProject()) {
			p.sendMessage("§cThis bench already has a project. SHIFT + LEFT CLICK with the branding tool to cancel first.");
			p.closeInventory();
			return;
		}
		station.setProject(project);
		markDirty();
		openMenu.remove(p.getUniqueId());
		p.closeInventory();
		p.sendMessage("§aSelected " + project.getName() + " §aas the current goldsmithing project");
		p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 2f);
	}

	@EventHandler
	public void onBreak(BlockBreakEvent e) {
		if (!isGoldsmithStation(e.getBlock())) return;
		Location loc = e.getBlock().getLocation();
		GoldsmithStation station = get(loc);
		if (station == null) return;
		List<ItemStack> refund = station.hasProject() ? station.cancel() : List.of();
		remove(loc);
		Player breaker = e.getPlayer();
		if (breaker != null) {
			giveOrDrop(breaker, refund);
		} else {
			dropAt(loc, refund);
		}
	}

	private void handleRightClick(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		ItemStack hand = p.getInventory().getItemInMainHand();
		GoldsmithStation existing = get(e.getClickedBlock().getLocation());

		if (existing == null || !existing.hasProject()) {
			markCooldown(p);
			GoldsmithStation station = getOrCreate(e.getClickedBlock().getLocation());
			openMenu.put(p.getUniqueId(), station);
			menus.openMenu(p);
			return;
		}

		if (isBranding(hand)) {
			markCooldown(p);
			sendStatus(p, existing);
			return;
		}

		if (isInfusedGem(hand)) {
			markCooldown(p);
			GoldsmithFeedback feedback = existing.addGem(hand);
			switch (feedback) {
				case SUCCESS:
					consumeOne(p);
					markDirty();
					p.sendTitle("§aAdded gem", "§7Gem §e1/1", 5, 20, 5);
					playWorkFx(existing.getLoc(), Material.GOLD_BLOCK);
					p.getWorld().playSound(existing.getLoc(), Sound.ITEM_AXE_WAX_OFF, 0.7f, 2f);
					break;
				case CAPACITY:
					p.sendMessage("§cThis bench already has a gem");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
					break;
				case WRONG_TYPE:
					p.sendMessage("§cThis project does not need a gem");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				case NO_PROJECT:
					p.sendMessage("§cThis bench has no project. Right-click the table to choose one.");
					break;
				default:
					break;
			}
			return;
		}

		GoldsmithMaterial material = matchMaterial(hand);
		if (material == null) return;

		markCooldown(p);
		GoldsmithFeedback feedback = existing.addMaterial(material, hand);
		switch (feedback) {
			case SUCCESS:
				consumeOne(p);
				markDirty();
				IntCounter bucket = existing.getTypes().get(material.getType());
				String progress = bucket == null ? "" : bucket.getCurrent() + "/" + bucket.getNeeded();
				p.sendTitle("§aAdded " + material.getName(), GoldsmithMaterialTypeLoader.display(material.getType()) + " §e" + progress, 5, 20, 5);
				playWorkFx(existing.getLoc(), Material.GOLD_BLOCK);
				p.getWorld().playSound(existing.getLoc(), Sound.ITEM_AXE_WAX_OFF, 0.7f, 2f);
				break;
			case CAPACITY:
				p.sendMessage("§cYou already have the needed amount of this type");
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
				break;
			case WRONG_TYPE:
				p.sendMessage("§cThis item type is not needed for the project");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case NO_PROJECT:
				p.sendMessage("§cThis bench has no project. Right-click the table to choose one.");
				break;
			default:
				break;
		}
	}

	private boolean handleLeftClick(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		GoldsmithStation station = get(e.getClickedBlock().getLocation());
		if (station == null || !station.hasProject()) return false;

		ItemStack hand = p.getInventory().getItemInMainHand();
		if (hand == null || hand.getType().isAir()) return false;

		if (isBranding(hand)) {
			markCooldown(p);
			if (p.isSneaking()) {
				List<ItemStack> refund = station.cancel();
				remove(station.getLoc());
				giveOrDrop(p, refund);
				p.sendMessage("§cProject cancelled");
				p.getWorld().playSound(station.getLoc(), Sound.ITEM_SHIELD_BREAK, 0.4f, 1f);
				return true;
			}
			GoldsmithFeedback finish = station.canFinish();
			if (finish == GoldsmithFeedback.LACKING_ITEMS) {
				p.sendMessage("§cYou have to add all the gold and the gem before finishing");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return true;
			}
			if (finish == GoldsmithFeedback.LACKING_HITS) {
				p.sendMessage("§cYou need to complete all the hits before finishing");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return true;
			}
			if (finish == GoldsmithFeedback.RECIPE_MISMATCH) {
				p.sendMessage("§cThe materials do not match the recipe");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return true;
			}
			completeCraft(p, station);
			return true;
		}

		NBTItem nbt = NBTItem.get(hand);
		if (!nbt.hasType()) return false;
		GoldsmithHit hit = GoldsmithHitLoader.getByTool(nbt.getType() + "." + nbt.getString("MMOITEMS_ITEM_ID"));
		if (hit == null) return false;

		markCooldown(p);
		GoldsmithFeedback feedback = station.hit(hit);
		switch (feedback) {
			case SUCCESS:
				IntCounter typeCounter = station.getHitTypes().get(hit.getType());
				markDirty();
				GoldsmithHitType type = hit.getType();
				String typeName = type == null ? "Hits" : type.getName();
				String progress = typeCounter == null ? "" : typeCounter.getCurrent() + "/" + typeCounter.getNeeded();
				p.sendTitle("§a+1 " + hit.getName(), typeName + " §e" + progress, 5, 20, 5);
				playWorkFx(station.getLoc(), Material.GOLD_BLOCK);
				p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_USE, 0.4f, 1f);
				break;
			case LACKING_ITEMS:
				p.sendMessage("§cYou have to add all the gold and the gem before working");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case WRONG_TYPE:
				p.sendMessage("§cThis item cannot be used for goldsmithing hits");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case NONE:
				p.sendMessage("§cThis tool is not needed for this project");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case CAPACITY:
				p.sendMessage("§cYou dont need more hits with this tool");
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
				break;
			default:
				break;
		}
		return true;
	}

	private void sendStatus(Player p, GoldsmithStation station) {
		p.sendMessage("§7Project: " + station.getProject().getName());
		for (Map.Entry<String, IntCounter> e : station.getTypes().entrySet()) {
			IntCounter c = e.getValue();
			p.sendMessage(GoldsmithMaterialTypeLoader.display(e.getKey()) + "§7: §e" + c.getCurrent() + "/" + c.getNeeded());
		}
		if (station.getProject().requiresGem()) {
			p.sendMessage("§7gem: §e" + (station.hasGem() ? "1/1" : "0/1"));
		}
		for (Map.Entry<GoldsmithHitType, IntCounter> e : station.getHitTypes().entrySet()) {
			IntCounter c = e.getValue();
			p.sendMessage(e.getKey().getName() + "§7: §e" + c.getCurrent() + "/" + c.getNeeded());
		}
		p.sendMessage("§cSHIFT + LEFT CLICK with the branding tool to cancel the project!");
	}

	private void completeCraft(Player p, GoldsmithStation station) {
		ItemStack output = JewelryOutput.build(station, p);
		if (output == null) {
			p.sendMessage("§cCould not create that item. Contact an administrator.");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		Location drop = station.getLoc().clone().add(0.5, 1, 0.5);
		if (drop.getWorld() != null) {
			drop.getWorld().dropItemNaturally(drop, output);
			drop.getWorld().playSound(drop, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			drop.getWorld().playSound(drop, Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
		}
		p.sendTitle("§aYou made a " + station.getProject().getName(), "", 5, 40, 10);
		station.cancel();
		remove(station.getLoc());
	}

	private boolean isBranding(ItemStack item) {
		return TLibs.getItemAPI().getChecker().checkItemWithPath(item, GoldsmithCache.brandingTool);
	}

	private boolean isInfusedGem(ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) return false;
		if (!"Infused Gemstone".equalsIgnoreCase(nbt.getString("MMOITEMS_DISPLAYED_TYPE"))) return false;
		Gemstone gem = ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID"));
		return gem != null;
	}

	private GoldsmithMaterial matchMaterial(ItemStack item) {
		if (item == null || item.getType().isAir()) return null;
		for (GoldsmithMaterial material : GoldsmithMaterialLoader.get().values()) {
			if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, material.getPath())) {
				return material;
			}
		}
		return null;
	}

	private void consumeOne(Player p) {
		ItemStack hand = p.getInventory().getItemInMainHand();
		if (hand == null || hand.getType().isAir()) return;
		hand.setAmount(hand.getAmount() - 1);
	}

	private void giveOrDrop(Player p, List<ItemStack> items) {
		for (ItemStack item : items) {
			HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
			for (ItemStack extra : leftover.values()) {
				p.getWorld().dropItemNaturally(p.getLocation(), extra);
			}
		}
	}

	private void dropAt(Location loc, List<ItemStack> items) {
		if (loc.getWorld() == null) return;
		Location drop = loc.clone().add(0.5, 1, 0.5);
		for (ItemStack item : items) {
			loc.getWorld().dropItemNaturally(drop, item);
		}
	}

	private void playWorkFx(Location loc, Material dust) {
		if (loc.getWorld() == null) return;
		loc.getWorld().spawnParticle(
				Particle.BLOCK_DUST,
				loc.clone().add(0.5, 1, 0.5),
				20, 0.1, 0.2, 0.1,
				dust.createBlockData());
	}

	private boolean onCooldown(Player p) {
		Long until = clickCooldown.get(p.getUniqueId());
		return until != null && System.currentTimeMillis() < until;
	}

	private void markCooldown(Player p) {
		clickCooldown.put(p.getUniqueId(), System.currentTimeMillis() + CLICK_COOLDOWN_MS);
	}
}
