package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Un variant de mob, codé en dur : conditions d'apparition et capacités. Chaque capacité part de valeurs de base propres
 * au variant, multipliées par le ratio du mob (ActiveMob#scale). Les méthodes on... ne font rien par défaut : un variant
 * n'écrit que celles dont il a besoin. Thread principal.
 */
public abstract class MobVariant {

    private final String id;
    private final EntityType type;
    private final Rarity rarity;
    private final String name;
    private MobService service;

    /**
     * @param id     identifiant stocké sur le mob : ne jamais le changer une fois en production
     * @param type   mob de Minecraft transformé
     * @param rarity chance d'être choisi parmi les variants du même mob
     * @param name   nom affiché dans le tag
     */
    protected MobVariant(String id, EntityType type, Rarity rarity, String name) {
        this.id = id;
        this.type = type;
        this.rarity = rarity;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public EntityType type() {
        return type;
    }

    public Rarity rarity() {
        return rarity;
    }

    public String name() {
        return name;
    }

    /** Appelé par MobService à sa création. */
    void bind(MobService service) {
        this.service = service;
    }

    /** Pour les variants qui font apparaître un variant (résurrection...). */
    protected MobService service() {
        return service;
    }

    /** Conditions d'apparition (biome, hauteur, lumière, heure, mob bébé...), au point d'apparition. Par défaut : partout. */
    public boolean canSpawn(LivingEntity entity) {
        return true;
    }

    /** Vie de base, en fois la vie normale du mob : multipliée ensuite par le ratio. */
    public double health() {
        return 1;
    }

    /** Dégâts de base, en fois les dégâts normaux du mob : multipliés ensuite par le ratio. */
    public double damage() {
        return 1;
    }

    /** Vitesse, en fois celle du mob : PAS multipliée par le ratio (un Fléau deux fois plus rapide serait injouable). */
    public double speed() {
        return 1;
    }

    /** Une seule fois, à la création du mob (rayon d'explosion, équipement...) : ce qui est posé ici est gardé par Minecraft. */
    public void onSpawn(ActiveMob mob) {
    }

    /** Chaque fois que le mob entre dans le monde (apparu ou rechargé) : ce que Minecraft ne sauvegarde pas (IA...). */
    public void onLoad(ActiveMob mob) {
    }

    /** Chaque fois que le mob sort du monde (mort ou déchargé) : oublier ce que le variant garde en mémoire pour lui. */
    public void onRemove(ActiveMob mob) {
    }

    /** Le mob frappe (de près, ou avec un projectile qu'il a tiré). */
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /** Le mob reçoit des dégâts. */
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
    }

    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
    }

    /** Le mob tire à l'arc (squelette) : la flèche est event.getProjectile(). */
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
    }

    /** Un projectile tiré par le mob touche quelque chose (bloc ou entité), avant les dégâts éventuels. */
    public void onProjectileHit(ActiveMob mob, ProjectileHitEvent event) {
    }

    /** Le mob prend une cible (event.getTarget(), jamais null ici). */
    public void onTarget(ActiveMob mob, EntityTargetLivingEntityEvent event) {
    }

    /** Le mob explose (creeper). */
    public void onExplode(ActiveMob mob, EntityExplodeEvent event) {
    }

    /**
     * Animation des Fléaux (ratio le plus fort) seulement, pour qu'ils se repèrent sans couvrir le monde de particules :
     * toutes les MobService.AMBIENT_PERIOD ticks, si un joueur est à moins de MobService.AMBIENT_RANGE blocs.
     */
    public void ambient(ActiveMob mob) {
    }

    /** Particules autour du mob, à height blocs de ses pieds, dispersées sur spread blocs (pour ambient). */
    protected static void puff(ActiveMob mob, Particle particle, int count, double height, double spread) {
        puff(mob, particle, count, height, spread, null);
    }

    /** Pareil, avec les données de la particule (couleur de Particle.DUST...). */
    protected static <T> void puff(ActiveMob mob, Particle particle, int count, double height, double spread, T data) {
        Location location = mob.entity().getLocation().add(0, height, 0);
        location.getWorld().spawnParticle(particle, location, count, spread, spread, spread, 0.01, data);
    }

    /**
     * Onde de choc : les joueurs à moins de radius blocs du centre (sauf créatif et spectateur) prennent damage (venant de
     * source) et sont poussés vers l'extérieur (push) et vers le haut (lift), en blocs par tick.
     */
    protected static void shockwave(LivingEntity source, Location center, double radius, double damage, double push, double lift) {
        Hits.shockwave(source, center, radius, damage, push, lift);
    }

    /**
     * Dégâts qui ignorent l'armure et les enchantements : retirés directement de la vie (cœurs d'absorption d'abord).
     * Un coup fatal passe par de vrais dégâts magiques venant de cause (null : sans auteur), pour le message de mort et
     * le totem d'immortalité. Magiques : ils ignorent l'armure et ne l'usent pas (des dégâts « infinis » qui passent par
     * l'armure la détruisent entièrement, au lieu qu'elle tombe à la mort). Rien en créatif ni en spectateur.
     */
    protected static void trueDamage(Player player, double damage, Entity cause) {
        Hits.trueDamage(player, damage, cause);
    }

    /** Toutes les combien de ticks appeler tick (multiple de MobService.TICK_PERIOD) ; 0 = jamais. */
    public int tickInterval() {
        return 0;
    }

    /** Capacité périodique : appelée seulement si un joueur est à moins de MobService.TICK_RANGE blocs. */
    public void tick(ActiveMob mob) {
    }
}
