package com.jedk1.jedcore.ability.earthbending;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.collision.CollisionUtil;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.policies.removal.*;
import com.jedk1.jedcore.util.TempFallingBlock;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class MudSurge extends EarthAbility implements AddonAbility {
	private int prepareRange;
	private int blindChance;
	private int blindTicks;
	private boolean multipleHits;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	private int waves;
	private int waterSearchRadius;
	private boolean wetSource;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute("CollisionRadius")
	private double collisionRadius;

	public static int surgeInterval = 300;
	public static int mudPoolRadius = 2;
	public static long mudCreationInterval = 100;
	public static Material[] mudTypes = new Material[] {
			Material.SAND, Material.CLAY, Material.TERRACOTTA, Material.BLACK_TERRACOTTA, Material.BLUE_TERRACOTTA,
			Material.BROWN_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.GREEN_TERRACOTTA,
			Material.LIGHT_BLUE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.LIME_TERRACOTTA,
			Material.MAGENTA_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.PINK_TERRACOTTA,
			Material.PURPLE_TERRACOTTA, Material.RED_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
			Material.GRASS_BLOCK, Material.DIRT, Material.MYCELIUM,
			Material.SOUL_SAND, Material.RED_SANDSTONE, Material.SANDSTONE, Material.SOUL_SOIL, Material.RED_SAND,
			Material.ROOTED_DIRT, Material.PODZOL, Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM,
			Material.BLACK_CONCRETE_POWDER, Material.BLUE_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER,
			Material.GRAY_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE_POWDER, 
			Material.LIME_CONCRETE_POWDER, Material.MAGENTA_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER, Material.PINK_CONCRETE_POWDER, 
			Material.PURPLE_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER, Material.WHITE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER
			};

	private CompositeRemovalPolicy removalPolicy;

	private Block source;
	private TempBlock sourceTB;

	private int wavesOnTheRun = 0;
	private long lastSurgeTime = 0;
	private boolean mudFormed = false;
	private boolean doNotSurge = false;
	public boolean started = false;

	private List<Block> mudArea = new ArrayList<>();
	private ListIterator<Block> mudAreaItr;
	private List<TempBlock> mudBlocks = new ArrayList<>();
	private List<Player> blind = new ArrayList<>();
	private List<Entity> affectedEntities = new ArrayList<>();

	private List<TempFallingBlock> fallingBlocks = new ArrayList<>();
	
	private Random rand = new Random();

	public MudSurge(Player player) {
		super(player);

		if (!bPlayer.canBend(this)) {
			return;
		}

		if (hasAbility(player, MudSurge.class)) {
			MudSurge ms = getAbility(player, MudSurge.class);
			if (!ms.hasStarted()) {
				ms.remove();
			} else {
				return;
			}
		}

		this.removalPolicy = new CompositeRemovalPolicy(this,
				new CannotBendRemovalPolicy(this.bPlayer, this, true, true),
				new IsOfflineRemovalPolicy(this.player),
				new IsDeadRemovalPolicy(this.player),
				new OutOfRangeRemovalPolicy(this.player, 25.0, () -> {
					return this.source.getLocation();
				}),
				new SwappedSlotsRemovalPolicy<>(bPlayer, MudSurge.class)
		);
		
		setFields();

		if (getSource()) {
			loadMudPool();
			start();
		}
	}
	
	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		this.removalPolicy.load(config);
		
		prepareRange = config.getInt("Abilities.Earth.MudSurge.SourceRange");
		blindChance = config.getInt("Abilities.Earth.MudSurge.BlindChance");
		damage = config.getDouble("Abilities.Earth.MudSurge.Damage");
		waves = config.getInt("Abilities.Earth.MudSurge.Waves");
		waterSearchRadius = config.getInt("Abilities.Earth.MudSurge.WaterSearchRadius");
		wetSource = config.getBoolean("Abilities.Earth.MudSurge.WetSourceOnly");
		cooldown = config.getLong("Abilities.Earth.MudSurge.Cooldown");
		blindTicks = config.getInt("Abilities.Earth.MudSurge.BlindTicks");
		multipleHits = config.getBoolean("Abilities.Earth.MudSurge.MultipleHits");
		collisionRadius = config.getDouble("Abilities.Earth.MudSurge.CollisionRadius");
	}

	@Override
	public void progress() {
		if (removalPolicy.shouldRemove()) {
			remove();
			return;
		}

		if (mudFormed && started && System.currentTimeMillis() > lastSurgeTime + surgeInterval) {
			surge();
			affect();
			if (TempFallingBlock.getFromAbility(this).isEmpty()) {
				remove();
				return;
			}
			return;
		}

		if (!mudFormed) {
			createMudPool();
		}
	}

	private boolean getSource() {
		Block block = getMudSourceBlock(prepareRange);

		if (block != null) {
			if (isMud(block)) {
				boolean water = true;

				if (wetSource) {
					water = false;
					List<Block> nearby = GeneralMethods.getBlocksAroundPoint(block.getLocation(), waterSearchRadius);

					for (Block b : nearby) {
						if (b.getType() == Material.WATER) {
							water = true;
							break;
						}
					}
				}

				if (water) {
					this.source = block;
					this.sourceTB = new TempBlock(this.source, Material.BROWN_TERRACOTTA, Material.BROWN_TERRACOTTA.createBlockData());
					return true;
				}
			}
		}

		return false;
	}

	private void startSurge() {
		started = true;
		this.bPlayer.addCooldown(this);

		// Clear out the policies that only apply while sourcing.
		this.removalPolicy.removePolicyType(IsDeadRemovalPolicy.class);
		this.removalPolicy.removePolicyType(OutOfRangeRemovalPolicy.class);
		this.removalPolicy.removePolicyType(SwappedSlotsRemovalPolicy.class);
	}

	private boolean hasStarted() {
		return this.started;
	}

	public static boolean isSurgeBlock(Block block) {
		if (block.getType() != Material.BROWN_TERRACOTTA) {
			return false;
		}

		for (MudSurge surge : CoreAbility.getAbilities(MudSurge.class)) {
			if (surge.mudArea.contains(block)) {
				return true;
			}
		}

		return false;
	}

	// Returns true if the event should be cancelled.
	public static boolean onFallDamage(Player player) {
		BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		if (bPlayer == null || !bPlayer.hasElement(Element.EARTH)) {
			return false;
		}

		ConfigurationSection config = JedCoreConfig.getConfig(player);

		boolean fallDamage = config.getBoolean("Abilities.Earth.MudSurge.AllowFallDamage");
		if (fallDamage) {
			return false;
		}

		Block block = player.getLocation().clone().subtract(0, 0.1, 0).getBlock();
		return isSurgeBlock(block);
	}

	public static void mudSurge(Player player) {
		if (!hasAbility(player, MudSurge.class))
			return;

		getAbility(player, MudSurge.class).startSurge();
	}

	private Block getMudSourceBlock(int range) {
		Block testBlock = GeneralMethods.getTargetedLocation(player, range, ElementalAbility.getTransparentMaterials()).getBlock();
		if (isMud(testBlock))
			return testBlock;

		Location loc = player.getEyeLocation();
		Vector dir = player.getEyeLocation().getDirection().clone().normalize();

		for (int i = 0; i <= range; i++) {
			Block block = loc.clone().add(dir.clone().multiply(i == 0 ? 1 : i)).getBlock();
			if (GeneralMethods.isRegionProtectedFromBuild(player, "MudSurge", block.getLocation()))
				continue;

			if (isMud(block))
				return block;
		}

		return null;
	}

	private boolean isMud(Block block) {
		for (Material mat : mudTypes) {
			if (mat.name().equalsIgnoreCase(block.getType().name()))
				return true;
		}

		return false;
	}

	private void createMud(Block block) {
		mudBlocks.add(new TempBlock(block, Material.BROWN_TERRACOTTA, Material.BROWN_TERRACOTTA.createBlockData()));
	}

	private void loadMudPool() {
		List<Location> area = GeneralMethods.getCircle(source.getLocation(), mudPoolRadius, 3, false, true, 0);

		for (Location l : area) {
			Block b = l.getBlock();

			if (isMud(b)) {
				if (isTransparent(b.getRelative(BlockFace.UP))) {
					boolean water = true;

					if (wetSource) {
						water = false;
						List<Block> nearby = GeneralMethods.getBlocksAroundPoint(l, waterSearchRadius);

						for (Block block : nearby) {
							if (block.getType() == Material.WATER) {
								water = true;
								break;
							}
						}
					}

					if (water) {
						mudArea.add(b);
						playEarthbendingSound(b.getLocation());
					}
				}
			}
		}

		mudAreaItr = mudArea.listIterator();
	}

	private void createMudPool() {
		if (!mudAreaItr.hasNext()) {
			mudFormed = true;
			return;
		}

		Block b = mudAreaItr.next();

		if (b != null)
			createMud(b);
	}

	private void revertMudPool() {
		for (TempBlock tb : mudBlocks)
			tb.revertBlock();

		mudBlocks.clear();
	}

	private void surge() {
		if (wavesOnTheRun >= waves) {
			doNotSurge = true;
			return;
		}

		if (doNotSurge)
			return;

		for (TempBlock tb : mudBlocks) {
			Vector direction = GeneralMethods.getDirection(tb.getLocation().add(0, 1, 0), GeneralMethods.getTargetedLocation(player, 30)).multiply(0.07);

			double x = rand.nextDouble() / 5;
			double z = rand.nextDouble() / 5;

			x = (rand.nextBoolean()) ? -x : x;
			z = (rand.nextBoolean()) ? -z : z;

			fallingBlocks.add(new TempFallingBlock(tb.getLocation().add(0, 1, 0), Material.BROWN_TERRACOTTA.createBlockData(), direction.clone().add(new Vector(x, 0.2, z)), this));
			
			playEarthbendingSound(tb.getLocation());
		}

		wavesOnTheRun++;
	}

	private void affect() {
		for (TempFallingBlock tfb : TempFallingBlock.getFromAbility(this)) {
			FallingBlock fb = tfb.getFallingBlock();
			if (fb.isDead()) {
				tfb.remove();
				continue;
			}

			for (Entity e : GeneralMethods.getEntitiesAroundPoint(fb.getLocation(), 1.5)) {
				if (fb.isDead()) {
					tfb.remove();
					continue;
				}
				if (GeneralMethods.isRegionProtectedFromBuild(this, e.getLocation()) || ((e instanceof Player) && Commands.invincible.contains(((Player) e).getName()))){
					continue;
				}

				if (e instanceof LivingEntity) {
					if (this.multipleHits || !this.affectedEntities.contains(e)) {
						DamageHandler.damageEntity(e, damage, this);
						if (!this.multipleHits) {
							this.affectedEntities.add(e);
						}
					}

					if (e instanceof Player) {
						if (e.getEntityId() == player.getEntityId())
							continue;

						if (rand.nextInt(100) < blindChance && !blind.contains(e)) {
							((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, this.blindTicks, 2));
						}

						blind.add((Player) e);
					}

					e.setVelocity(fb.getVelocity().multiply(0.8));
					tfb.remove();
				}
			}
		}
	}

	@Override
	public void remove() {
		sourceTB.revertBlock();
		revertMudPool();
		super.remove();
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public List<Location> getLocations() {
		return fallingBlocks.stream().map(TempFallingBlock::getLocation).collect(Collectors.toList());
	}

	@Override
	public void handleCollision(Collision collision) {
		CollisionUtil.handleFallingBlockCollisions(collision, fallingBlocks);
	}

	@Override
	public double getCollisionRadius() {
		return collisionRadius;
	}

	@Override
	public String getName() {
		return "MudSurge";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public String getAuthor() {
		return JedCore.dev;
	}

	@Override
	public String getVersion() {
		return JedCore.version;
	}

	@Override
	public String getDescription() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return "* JedCore Addon *\n" + config.getString("Abilities.Earth.MudSurge.Description");
	}

	@Override
	public void load() {

	}

	@Override
	public void stop() {

	}

	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Earth.MudSurge.Enabled");
	}
}
