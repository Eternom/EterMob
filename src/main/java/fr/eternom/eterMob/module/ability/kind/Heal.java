package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/**
 * heal : se soigne de amount (part de sa vie max, 0,15) sous below (0,5 de sa vie), après un long télégraphe (2 s) : un
 * étourdissement ou un silence l'interrompt, c'est la réponse des joueurs. Les boss restent plafonnés par leur phase.
 */
public final class Heal extends Ability {

    private final double amount;

    public Heal(ConfigurationSection config) {
        super("heal", config, new Defaults(Trigger.ACTIVE, 25, 2, 0, 64, 1, 0, 0, 0, 0));
        this.amount = Math.clamp(config.getDouble("amount", 0.15), 0.01, 0.5);
    }

    @Override
    public boolean inRange(double distance, boolean hasSight) {
        return true;
    }

    @Override
    public double score(AbilityContext context) {
        double health = context.situation().selfHealth();
        if (health >= below) {
            return 0;
        }
        // Plus intéressant quand peu de joueurs le frappent au contact (sinon il se fait interrompre)
        return clamp01(0.4 + (below - health) * 1.5 - 0.15 * context.situation().playersNearSelf());
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        context.self().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, context.self().getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        AttributeInstance max = self.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        self.setHealth(Math.min(max.getValue(), self.getHealth() + max.getValue() * amount));
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 1.4f);
        self.getWorld().spawnParticle(Particle.HEART, self.getLocation().add(0, self.getHeight(), 0), 5, 0.4, 0.3, 0.4, 0);
    }

    @Override
    public double value() {
        return amount * 40;
    }
}
