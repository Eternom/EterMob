package fr.eternom.eterMob.module.mob;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tous les variants et meneurs, codés en dur (module/variant) et déclarés au démarrage (register). Identifiant :
 * [a-z0-9_-], jamais changé une fois en jeu (il est écrit sur le mob).
 */
public class Variants {

    private final Map<String, MobVariant> byId = new LinkedHashMap<>();
    private final Set<String> disabled;

    public Variants(Set<String> disabled) {
        this.disabled = disabled;
    }

    public void register(MobVariant variant) {
        if (byId.putIfAbsent(variant.id(), variant) != null) {
            throw new IllegalArgumentException("Deux variants ont l'identifiant " + variant.id());
        }
    }

    public MobVariant get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<MobVariant> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Désactivé (disabled-variants) : n'apparaît plus de lui-même (commande et invocation restent possibles). */
    public boolean disabled(String id) {
        return disabled.contains(id);
    }
}
