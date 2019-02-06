package com.biggestnerd.quickfix;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import net.minecraft.server.v1_12_R1.CraftingManager;
import net.minecraft.server.v1_12_R1.IRecipe;

@SuppressWarnings("deprecation")
public class QuickFix extends JavaPlugin implements Listener {

	private Set<Material> disabledCrafting;
	private Map<Integer, Long> populateTime;
	private boolean allowNetherTravel;
	private Set<Material> disallow;
	private boolean hostileSpawnerOnly;
	private boolean stopTrapHorses;
	private Set<Material> noPlace;
	private List<String> disabledCommands;
	private Set<EntityType> disabledMobs = new HashSet<EntityType>();
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfig();
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("qf").setExecutor(this);
		getCommand("broadcast").setExecutor(this);
		populateTime = new HashMap<Integer, Long>();
		uncraftNetherBlocks();
		if(getServer().getPluginManager().isPluginEnabled("HiddenOre")) {
			List<String> worlds = getConfig().contains("hiddenoreworlds") && getConfig().isList("hiddenoreworlds") 
					? getConfig().getStringList("hiddenoreworlds") : Arrays.asList(new String[]{"world"});
			getServer().getPluginManager().registerEvents(new HiddenOreListener(worlds), this);
		}
	}
	
	private void loadConfig() {
		saveDefaultConfig();
		reloadConfig();
		FileConfiguration config = getConfig();
		disabledCrafting = new HashSet<Material>();
		if(config.contains("disabled_recipes")) {
			for(String name : config.getStringList("disabled_recipes")) {
				Material mat = Material.getMaterial(name);
				if(mat != null) {
					disabledCrafting.add(mat);
				}
			}
			disableRecipes();
		}
		allowNetherTravel = config.getBoolean("allow_nether_travel", false);
		hostileSpawnerOnly = config.getBoolean("hostile_spawners_only", true);
		disallow = new HashSet<Material>();
		for(String name : config.getStringList("disallow")) {
			disallow.add(Material.getMaterial(name));
		}
		noPlace = new HashSet<Material>();
		for(String name : config.getStringList("noplace")) {
			noPlace.add(Material.getMaterial(name));
		}
		stopTrapHorses = config.getBoolean("stop_trap_horses", true);
		disabledCommands = config.getStringList("disabled_commands");
		if (config.contains("disabled_entities")) {
			for(String entity: config.getStringList("disabled_entities")) {
				disabledMobs.add(EntityType.valueOf(entity));
			}
		}
	}
	
	private void disableRecipes() {
		Iterator<IRecipe> iter = CraftingManager.recipes.iterator();
		while(iter.hasNext()) {
			if(disabledCrafting.contains(iter.next().toBukkitRecipe().getResult().getType())) {
				iter.remove();
			}
		}
	}
	
	private void uncraftNetherBlocks() {
		ItemStack warts = new ItemStack(Material.NETHER_STALK, 9);
		ShapelessRecipe uncraftWarts = new ShapelessRecipe(warts);
		uncraftWarts.addIngredient(Material.NETHER_WART_BLOCK);
		getServer().addRecipe(uncraftWarts);
	}
	
	@EventHandler
	public void onPrepareItemCraft(PrepareItemCraftEvent event) {
		if(disabledCrafting.contains(event.getRecipe().getResult().getType())) {
			event.getInventory().setResult(new ItemStack(Material.AIR));
		}
	}
	
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		populateTime.put(event.getChunk().hashCode(), System.currentTimeMillis());
	}
	
	@EventHandler
	public void onEntitySpawn(CreatureSpawnEvent event) {
		if(disabledMobs.contains(event.getEntityType())) {
			event.setCancelled(true);
			return;
		}
		if(!populateTime.containsKey(event.getLocation().getChunk().hashCode()) || populateTime.get(event.getLocation().getChunk().hashCode()) > 60000) return;
		Environment env = event.getLocation().getWorld().getEnvironment();
		if(env == Environment.NETHER) {
			if(event.getEntityType() != EntityType.PIG_ZOMBIE && event.getEntityType() != EntityType.SKELETON
					&& event.getEntityType() != EntityType.MAGMA_CUBE && event.getEntityType() != EntityType.GHAST) {
				event.setCancelled(true);
			}
		}
		if(env == Environment.THE_END) {
			if(event.getEntityType() != EntityType.ENDERMAN && event.getEntityType() != EntityType.ENDER_DRAGON) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		if(stopTrapHorses && event.getEntityType() == EntityType.SKELETON) {
			Entity entity = event.getEntity();
			if(entity.isInsideVehicle() && entity.getVehicle() instanceof Horse) {
				entity.remove();
				entity.getVehicle().remove();
			}
		}
	}
	
	@EventHandler
	public void onBlockFromTo(BlockFromToEvent event) {
		if(event.getBlock().getType() != Material.STATIONARY_LAVA &&
				event.getBlock().getType() != Material.LAVA) {
			return;
		}
		Block to = event.getToBlock();
		if(to.getType() != Material.REDSTONE && to.getType() != Material.TRIPWIRE) {
			return;
		}
		if(to.getRelative(BlockFace.NORTH).getType() == Material.STATIONARY_WATER
			|| to.getRelative(BlockFace.SOUTH).getType() == Material.STATIONARY_WATER
			|| to.getRelative(BlockFace.WEST).getType() == Material.STATIONARY_WATER
			|| to.getRelative(BlockFace.EAST).getType() == Material.STATIONARY_WATER
			|| to.getRelative(BlockFace.NORTH).getType() == Material.WATER
			|| to.getRelative(BlockFace.SOUTH).getType() == Material.WATER
			|| to.getRelative(BlockFace.WEST).getType() == Material.WATER
			|| to.getRelative(BlockFace.EAST).getType() == Material.WATER) {
			to.setType(Material.OBSIDIAN);
		}
	}
	
	@EventHandler
	public void onTouchBedrock(PlayerInteractEvent event) {
		if(event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.BEDROCK && event.getClickedBlock().getY() > 0) {
			Bukkit.getScheduler().runTask(this, () -> {
				event.getClickedBlock().setType(Material.STONE);
			});
		}
	}
	
	@EventHandler
	public void onPlayerPortal(PlayerPortalEvent event) {
		if(!allowNetherTravel && event.getCause() == TeleportCause.NETHER_PORTAL) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		event.setDeathMessage(null);
		String killer = "";
		try {
			killer = event.getEntity().getLastDamageCause().getCause().toString();
		} catch (NullPointerException e) {
			//Lots of things there can be null, not gonna bother to check them all lol
			return;
		}
		if(event.getEntity().getKiller() != null) {
			killer = event.getEntity().getKiller().getDisplayName();
		}
		Location loc = event.getEntity().getLocation();
		event.getEntity().sendMessage(ChatColor.RED + "You were slain by " + killer + " at ["
				+ loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +"]");
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if(event.getCurrentItem() != null && disallow.contains(event.getCurrentItem().getType())) {
			event.getWhoClicked().sendMessage(ChatColor.RED + "You're not supposed to have that!");
			event.setCurrentItem(null);
		}
	}
	
	static final List<EntityType> hostiles = Arrays.asList(new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SILVERFISH, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.BLAZE});
	Random rng = new Random();
	@EventHandler
	public void onSpawnerSpawn(SpawnerSpawnEvent event) {
		if(hostileSpawnerOnly && !hostiles.contains(event.getEntityType())) {
			event.setCancelled(true);
			event.getSpawner().setSpawnedType(hostiles.get(rng.nextInt(hostiles.size())));
		}
	}
	
	@EventHandler
	public void onBlockInteract(PlayerInteractEvent event) {
		if(hostileSpawnerOnly && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.MOB_SPAWNER &&
				event.getItem() != null && event.getItem().getType() == Material.MONSTER_EGG) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if(noPlace.contains(event.getBlock().getType())) {
			event.setCancelled(true);
			event.getPlayer().sendMessage(ChatColor.RED + "You're not allowed to place that!");
		}
	}
	
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent event) {
		String command = event.getMessage().split(" ")[0];
		if(command.startsWith("/")) {
			command = command.substring(1);
		}
		if(disabledCommands.contains(command)) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if(event.getPlayer().getLocation().getY() < 1) {
			if(event.getPlayer().hasPermission("bedrock.bypass") || event.getPlayer().isOp()) {
				return;
			}
			getLogger().info(event.getPlayer().getName() + " is below bedrock, attempting to teleport them up");
			Bukkit.getScheduler().runTaskLater(this, new Runnable() {
				public void run() {
					if(!tryToTeleport(event.getPlayer(), event.getPlayer().getLocation())) {
						event.getPlayer().setHealth(0);
						getLogger().info(event.getPlayer().getName() + " died to the void at " + event.getPlayer().getLocation());
					}
				}
			}, 2L);
		}
	}
	
	private boolean tryToTeleport(Player player, Location location) {
		Location loc = location.clone();
		loc.setX(Math.floor(loc.getX()) + 0.5);
		loc.setY(Math.floor(loc.getY()) + 0.2);
		loc.setZ(Math.floor(loc.getZ()) + 0.5);
		final Location baseLoc = loc.clone();
		final World world = baseLoc.getWorld();
		boolean performTeleport = checkForTeleportSpace(loc);
		if(!performTeleport) {
			loc.setY(loc.getY() + 1);
			performTeleport = checkForTeleportSpace(loc);
		}
		if(performTeleport) {
			player.setVelocity(new Vector());
			player.teleport(loc);
			return true;
		}
		loc = baseLoc.clone();
		int airCount = 0;
		LinkedList<Material> airWindow = new LinkedList<Material>();
		loc.setY((float) world.getMaxHeight() - 2);
		Block block = world.getBlockAt(loc);
		for(int i = 0; i < 4; i++) {
			Material blockMat = block.getType();
			if(!blockMat.isSolid()) {
				airCount++;
			}
			airWindow.addLast(blockMat);
			block = block.getRelative(BlockFace.DOWN);
		}
		
		while(block.getY() >= 1) {
			Material blockMat = block.getType();
			if(blockMat.isSolid()) {
				if(airCount == 4) {
					player.setVelocity(new Vector());
					loc = block.getLocation();
					loc.setX(Math.floor(loc.getX()) + 0.5);
					loc.setY(Math.floor(loc.getY()) + 1.02);
					loc.setZ(Math.floor(loc.getZ()) + 0.5);
					player.teleport(loc);
					return true;
				}
			} else {
				airCount++;
			}
			airWindow.addLast(blockMat);
			if(!airWindow.removeFirst().isSolid()) {
				airCount--;
			}
			block = block.getRelative(BlockFace.DOWN);
		}
		return false;
	}
	
	private boolean checkForTeleportSpace(Location loc) {
		final Block block = loc.getBlock();
		final Material mat = block.getType();
		if(mat.isSolid()) {
			return false;
		}
		final Block above = block.getRelative(BlockFace.UP);
		if(above.getType().isSolid()) {
			return false;
		}
		return true;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getName().equals("broadcast")) {
			if(args.length == 0) {
				sender.sendMessage(ChatColor.RED + "Specify a message to broadcast!");
				return true;
			}
			StringBuilder msg = new StringBuilder();
			msg.append(ChatColor.DARK_AQUA);
			msg.append("[Broadcast] ");
			for(String arg : args) {
				msg.append(arg).append(" ");
			}
			Bukkit.broadcastMessage(msg.toString());
			return true;
		}
		if(args.length < 1) {
			sender.sendMessage(ChatColor.RED + "Invalid arguments, do /qf <option|reload>");
			return true;
		}
		if(args[0].equals("reload")) {
			loadConfig();
			sender.sendMessage(ChatColor.GOLD + "QuickFix reloaded");
		} else {
			sender.sendMessage(ChatColor.GREEN + "[QuickFix] " + args[0] + ": " + getConfig().get(args[0]));
		}
		return true;
	}
}
