package me.Plugins.GemInfusion.goldsmith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.Objects.Utils.IntCounter;

public class GoldsmithStation {

	private final Location loc;
	private JewelryProject project;
	private ItemStack gem;

	private final LinkedHashMap<String, IntCounter> types = new LinkedHashMap<>();
	private final LinkedHashMap<GoldsmithHit, IntCounter> hits = new LinkedHashMap<>();
	private final LinkedHashMap<GoldsmithHitType, IntCounter> hitTypes = new LinkedHashMap<>();
	private final List<ItemStack> deposited = new ArrayList<>();
	private final LinkedHashMap<GoldsmithMaterial, Integer> depositedByMaterial = new LinkedHashMap<>();

	public GoldsmithStation(Location loc) {
		this.loc = loc;
	}

	public Location getLoc() {
		return loc;
	}

	public JewelryProject getProject() {
		return project;
	}

	public boolean hasProject() {
		return project != null;
	}

	public ItemStack getGem() {
		return gem;
	}

	public boolean hasGem() {
		return gem != null;
	}

	public Map<String, IntCounter> getTypes() {
		return Collections.unmodifiableMap(types);
	}

	public Map<GoldsmithHit, IntCounter> getHits() {
		return Collections.unmodifiableMap(hits);
	}

	public Map<GoldsmithHitType, IntCounter> getHitTypes() {
		return Collections.unmodifiableMap(hitTypes);
	}

	public List<ItemStack> getDeposited() {
		return Collections.unmodifiableList(deposited);
	}

	public Map<GoldsmithMaterial, Integer> getDepositedByMaterial() {
		return Collections.unmodifiableMap(depositedByMaterial);
	}

	public void setProject(JewelryProject project) {
		this.project = project;
		types.clear();
		hits.clear();
		hitTypes.clear();
		deposited.clear();
		depositedByMaterial.clear();
		gem = null;
		if (project == null) return;

		for (Map.Entry<String, Integer> e : project.getMaterialsByType().entrySet()) {
			IntCounter c = new IntCounter();
			c.setNeeded(e.getValue());
			types.put(e.getKey(), c);
		}
		for (Map.Entry<GoldsmithHit, Integer> e : project.getHits().entrySet()) {
			IntCounter c = new IntCounter();
			c.setNeeded(e.getValue());
			hits.put(e.getKey(), c);
		}
		for (Map.Entry<GoldsmithHitType, Integer> e : project.getHitsByType().entrySet()) {
			IntCounter c = new IntCounter();
			c.setNeeded(e.getValue());
			hitTypes.put(e.getKey(), c);
		}
	}

	/**
	 * Applies saved counters and item copies after {@link #setProject(JewelryProject)}.
	 * Needed values stay on the live project; only currents, deposited stacks, and gem are restored.
	 */
	public void applySavedProgress(Map<String, Integer> materials, Map<String, Integer> hitCounts,
			List<ItemStack> savedDeposited, ItemStack savedGem) {
		deposited.clear();
		depositedByMaterial.clear();
		gem = null;
		for (IntCounter c : types.values()) {
			c.setCurrent(0);
		}
		if (materials != null) {
			for (Map.Entry<String, Integer> e : materials.entrySet()) {
				GoldsmithMaterial material = GoldsmithMaterialLoader.getByString(e.getKey());
				if (material == null || e.getValue() == null) continue;
				int amount = Math.max(0, e.getValue());
				depositedByMaterial.put(material, amount);
				IntCounter bucket = types.get(material.getType());
				if (bucket != null) bucket.increaseCurrent(amount);
			}
		}
		if (hitCounts != null) {
			for (Map.Entry<String, Integer> e : hitCounts.entrySet()) {
				GoldsmithHit hit = GoldsmithHitLoader.getByString(e.getKey());
				if (hit == null || e.getValue() == null) continue;
				IntCounter counter = hits.get(hit);
				if (counter == null) {
					counter = new IntCounter();
					hits.put(hit, counter);
				}
				counter.setCurrent(Math.max(0, e.getValue()));
			}
			for (IntCounter c : hitTypes.values()) {
				c.setCurrent(0);
			}
			for (Map.Entry<GoldsmithHit, IntCounter> e : hits.entrySet()) {
				GoldsmithHitType type = e.getKey().getType();
				if (type == null) continue;
				IntCounter bucket = hitTypes.get(type);
				if (bucket != null) bucket.increaseCurrent(e.getValue().getCurrent());
			}
		}
		if (savedDeposited != null) {
			deposited.addAll(savedDeposited);
		}
		if (savedGem != null) {
			gem = savedGem.clone();
			gem.setAmount(1);
		}
	}

	public GoldsmithFeedback addMaterial(GoldsmithMaterial material, ItemStack stack) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (material == null) return GoldsmithFeedback.WRONG_TYPE;

		String type = material.getType();
		IntCounter bucket = types.get(type);
		if (bucket == null) return GoldsmithFeedback.WRONG_TYPE;
		if (bucket.isEqual()) return GoldsmithFeedback.CAPACITY;

		bucket.increaseCurrent(1);
		depositedByMaterial.merge(material, 1, Integer::sum);
		if (stack != null) {
			ItemStack copy = stack.clone();
			copy.setAmount(1);
			deposited.add(copy);
		}
		return GoldsmithFeedback.SUCCESS;
	}

	public GoldsmithFeedback addGem(ItemStack stack) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (!project.requiresGem()) return GoldsmithFeedback.WRONG_TYPE;
		if (gem != null) return GoldsmithFeedback.CAPACITY;
		if (stack == null) return GoldsmithFeedback.WRONG_TYPE;
		ItemStack copy = stack.clone();
		copy.setAmount(1);
		gem = copy;
		return GoldsmithFeedback.SUCCESS;
	}

	public GoldsmithFeedback hit(GoldsmithHit hit) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (!checkItems()) return GoldsmithFeedback.LACKING_ITEMS;
		if (hit == null || hit.getType() == null) return GoldsmithFeedback.WRONG_TYPE;
		if (!hitTypes.containsKey(hit.getType())) return GoldsmithFeedback.NONE;
		if (hitTypes.get(hit.getType()).isEqual()) return GoldsmithFeedback.CAPACITY;

		if (hits.containsKey(hit)) {
			hits.get(hit).increaseCurrent(1);
		} else {
			IntCounter counter = new IntCounter();
			counter.setCurrent(1);
			hits.put(hit, counter);
		}
		hitTypes.get(hit.getType()).increaseCurrent(1);
		return GoldsmithFeedback.SUCCESS;
	}

	public boolean checkItems() {
		if (project == null) return false;
		for (IntCounter c : types.values()) {
			if (!c.isEqual()) return false;
		}
		if (project.requiresGem() && gem == null) return false;
		return true;
	}

	public boolean checkExactHits() {
		if (project == null) return false;
		for (GoldsmithHit required : project.getHits().keySet()) {
			IntCounter c = hits.get(required);
			if (c == null || !c.isEqual()) return false;
		}
		for (Map.Entry<GoldsmithHit, IntCounter> e : hits.entrySet()) {
			if (!e.getValue().isEqual()) return false;
		}
		return true;
	}

	public boolean checkExactRecipe() {
		if (project == null) return false;
		Map<GoldsmithMaterial, Integer> recipe = project.getRecipe();
		if (depositedByMaterial.size() != recipe.size()) return false;
		for (Map.Entry<GoldsmithMaterial, Integer> e : recipe.entrySet()) {
			Integer have = depositedByMaterial.get(e.getKey());
			if (have == null || !have.equals(e.getValue())) return false;
		}
		return true;
	}

	public GoldsmithFeedback canFinish() {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (!checkItems()) return GoldsmithFeedback.LACKING_ITEMS;
		if (!checkExactHits()) return GoldsmithFeedback.LACKING_HITS;
		if (!checkExactRecipe()) return GoldsmithFeedback.RECIPE_MISMATCH;
		return GoldsmithFeedback.SUCCESS;
	}

	public List<ItemStack> cancel() {
		List<ItemStack> refund = new ArrayList<>(deposited);
		if (gem != null) refund.add(gem);
		project = null;
		gem = null;
		types.clear();
		hits.clear();
		hitTypes.clear();
		deposited.clear();
		depositedByMaterial.clear();
		return refund;
	}
}
