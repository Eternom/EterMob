package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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

    /** Conditions d'apparition (biome, hauteur, lumière, heure...). Par défaut : partout. */
    public boolean canSpawn(Location location) {
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

    /** Le mob frappe (de près, ou avec un projectile qu'il a tiré). */
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /** Le mob reçoit des dégâts. */
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
    }

    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
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

    /** Toutes les combien de ticks appeler tick (multiple de MobService.TICK_PERIOD) ; 0 = jamais. */
    public int tickInterval() {
        return 0;
    }

    /** Capacité périodique : appelée seulement si un joueur est à moins de MobService.TICK_RANGE blocs. */
    public void tick(ActiveMob mob) {
    }
}
