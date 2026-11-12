package fr.eternom.eterMob.module.loot;

import fr.eternom.eterLib.helper.ranking.RankingSource;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;
import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monstres tués par joueur, variant et ratio (table etermob_kills) : bestiaire, quêtes, et deux classements EterLib :
 * mob_kills (tous les monstres d'EterMob) et mob_elites (ratio 4 et plus : Élites et Fléaux).
 */
public final class KillStats {

    private static final String TABLE = "kills";
    private static final int ELITE = 4;

    private final JavaPlugin plugin;
    private final Database database;

    public KillStats(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("variant", Column.Type.STRING).length(64).primaryKey(),
                Column.of("ratio", Column.Type.INT).primaryKey(),
                Column.of("kills", Column.Type.LONG).notNull());
        Bukkit.getServicesManager().register(RankingSource.class, source("mob_kills", 1), plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(RankingSource.class, source("mob_elites", ELITE), plugin, ServicePriority.Normal);
    }

    /** +1 (en tâche de fond). */
    public void add(UUID player, String variant, int ratio) {
        Tasks.async(plugin, () -> database.execute("INSERT INTO " + database.table(TABLE)
                + " (uuid, variant, ratio, kills) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE kills = kills + 1",
                player, variant, ratio), "Monstre tué non compté");
    }

    /** Bloquant : tous les kills du joueur, variant -> (ratio -> kills), pour le bestiaire. */
    public java.util.Map<String, java.util.Map<Integer, Long>> all(UUID player) {
        java.util.Map<String, java.util.Map<Integer, Long>> kills = new java.util.HashMap<>();
        for (Row row : database.query("SELECT variant, ratio, kills FROM " + database.table(TABLE) + " WHERE uuid = ?", player)) {
            kills.computeIfAbsent(row.getString("variant"), id -> new java.util.TreeMap<>()).put(row.getInt("ratio"), row.getLong("kills"));
        }
        return kills;
    }

    /** Bloquant : combien de fois ce joueur a tué ce variant (tous ratios). */
    public long kills(UUID player, String variant) {
        List<Row> rows = database.query("SELECT SUM(kills) AS total FROM " + database.table(TABLE) + " WHERE uuid = ? AND variant = ?",
                player, variant);
        return rows.isEmpty() || rows.getFirst().get("total") == null ? 0 : rows.getFirst().getLong("total");
    }

    private RankingSource source(String id, int minRatio) {
        return new RankingSource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Ranked> top(int limit) {
                List<Ranked> ranked = new ArrayList<>();
                for (Row row : database.query("SELECT uuid, SUM(kills) AS total FROM " + database.table(TABLE)
                        + " WHERE ratio >= ? GROUP BY uuid ORDER BY total DESC LIMIT ?", minRatio, limit)) {
                    ranked.add(new Ranked(row.getUUID("uuid"), row.getLong("total")));
                }
                return ranked;
            }

            @Override
            public long[] of(UUID player) {
                List<Row> mine = database.query("SELECT SUM(kills) AS total FROM " + database.table(TABLE)
                        + " WHERE uuid = ? AND ratio >= ?", player, minRatio);
                long value = mine.isEmpty() || mine.getFirst().get("total") == null ? 0 : mine.getFirst().getLong("total");
                if (value == 0) {
                    return new long[]{0, 0};
                }
                List<Row> above = database.query("SELECT COUNT(*) AS rank FROM (SELECT uuid, SUM(kills) AS total FROM "
                        + database.table(TABLE) + " WHERE ratio >= ? GROUP BY uuid) t WHERE t.total > ?",
                        minRatio, value);
                long rank = above.isEmpty() ? 0 : above.getFirst().getLong("rank") + 1;
                return new long[]{rank, value};
            }
        };
    }
}
