package com.jedk1.jedcore.ability.firebending;

import com.jedk1.jedcore.collision.CollisionDetector;
import com.jedk1.jedcore.collision.Sphere;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.util.AirShieldReflector;
import com.jedk1.jedcore.util.FireTick;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.airbending.AirShield;
import com.projectkorra.projectkorra.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.jedk1.jedcore.JedCore;
import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.BlueFireAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.firebending.BlazeArc;
import com.projectkorra.projectkorra.firebending.util.FireDamageTimer;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;

public class FireBall extends FireAbility implements AddonAbility {
	private Location location;
	private Vector direction;
	private double distanceTravelled;

	@Attribute(Attribute.RANGE)
	private long range;
	@Attribute(Attribute.FIRE_TICK)
	private long fireticks;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	private boolean controllable;
	private boolean fireTrail;
	@Attribute("CollisionRadius")
	private double collisionRadius;

	public FireBall(Player player){
		super(player);
		if (!bPlayer.canBend(this)) {
			return;
		}
		
		setFields();
		
		location = player.getEyeLocation();
		direction = player.getEyeLocation().getDirection().normalize();

		bPlayer.addCooldown(this);
		start();
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		range = config.getLong("Abilities.Fire.FireBall.Range");
		fireticks = config.getLong("Abilities.Fire.FireBall.FireDuration");
		cooldown = config.getLong("Abilities.Fire.FireBall.Cooldown");
		damage = config.getDouble("Abilities.Fire.FireBall.Damage");
		controllable = config.getBoolean("Abilities.Fire.FireBall.Controllable");
		fireTrail = config.getBoolean("Abilities.Fire.FireBall.FireTrail");
		collisionRadius = config.getDouble("Abilities.Fire.FireBall.CollisionRadius");
		
		applyModifiers();
	}
	
	private void applyModifiers() {
		if (bPlayer.canUseSubElement(SubElement.BLUE_FIRE)) {
			range *= BlueFireAbility.getRangeFactor();
			cooldown *= BlueFireAbility.getCooldownFactor();
			damage *= BlueFireAbility.getDamageFactor();
		}
		
		if (isDay(player.getWorld())) {
			range = (long) getDayFactor(range);
			cooldown -= ((long) getDayFactor(cooldown) - cooldown);
			damage = getDayFactor(damage);
		}
	}
	
	@Override
	public void progress(){
		if(player.isDead() || !player.isOnline()){
			remove();
			return;
		}

		if(distanceTravelled >= range){
			remove();
			return;
		}

		if (GeneralMethods.isRegionProtectedFromBuild(player, "FireBall", location)) {
			remove();
			return;
		}

		progressFireball();
	}
	
	private void progressFireball(){
		for(int i = 0; i < 2; i++){
			distanceTravelled ++;
			if (distanceTravelled >= range) {
				return;
			}

			if (controllable) {
				direction = player.getLocation().getDirection();
			}
			
			location = location.add(direction.clone().multiply(1));
			if(GeneralMethods.isSolid(location.getBlock()) || isWater(location.getBlock())){
				distanceTravelled = range;
				return;
			}

			ParticleEffect.SMOKE_LARGE.display(location, 1, 0, 0, 0, 0);
			ParticleEffect.SMOKE_LARGE.display(location, 1, 0, 0, 0, 0);
			for (int j = 0; j < 5; j++) {
				playFirebendingParticles(location, 1, 0, 0, 0);
			}

			boolean hitTarget = CollisionDetector.checkEntityCollisions(player, new Sphere(location.toVector(), collisionRadius), this::doDamage);

			if (!hitTarget) {
				if (this.distanceTravelled > 2 && this.fireTrail) {
					new BlazeArc(player, location.clone().subtract(direction).subtract(direction), direction, 2);
				}
			} else {
				remove();
				return;
			}
		}
	}
	
	private boolean doDamage(Entity entity){
		if (!(entity instanceof LivingEntity)) return false;

		distanceTravelled = range;
		DamageHandler.damageEntity(entity, damage, this);

		FireTick.set(entity, Math.round(fireticks / 50));
		new FireDamageTimer(entity, player);
		return false;
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return location;
	}

	@Override
	public void handleCollision(Collision collision) {
		if (collision.isRemovingFirst()) {
			remove();
		} else {
			CoreAbility second = collision.getAbilitySecond();
			if (second instanceof AirShield) {
				ConfigurationSection config = JedCoreConfig.getConfig(this.player);
				boolean reflect = config.getBoolean("Abilities.Fire.FireBall.Collisions.AirShield.Reflect", true);

				if (reflect) {
					AirShield shield = (AirShield) second;
					AirShieldReflector.reflect(shield, this.location, this.direction);
				}
			}
		}
	}

	@Override
	public String getName() {
		return "FireBall";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
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
		return "* JedCore Addon *\n" + config.getString("Abilities.Fire.FireBall.Description");
	}

	@Override
	public void load() {
		return;
	}

	@Override
	public void stop() {
		return;
	}
	
	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Fire.FireBall.Enabled");
	}
}
