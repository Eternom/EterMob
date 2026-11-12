package fr.eternom.eterMob.module.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Les modèles Blockbench de chaque serveur (survie, ressources, donjons...) viennent du même endroit que le pack des
 * joueurs : le manifeste public (pack.json) publié par EterCraft/resourcepack. Au démarrage puis toutes les
 * poll-minutes, si l'archive des modèles a changé, elle remplace le contenu de plugins/BetterModel/models (dossier géré
 * par EterMob) et BetterModel est rechargé. Le pack des joueurs et les modèles des serveurs sortent du même build : ils
 * correspondent toujours. BetterModel doit générer des noms stables : use-obfuscation est remis à false.
 */
public final class ModelSync {

    /** Un fichier de modèle valide : l'identifiant du jeu, rien d'autre (pas de dossier, pas de « .. »). */
    private static final Pattern MODEL_FILE = Pattern.compile("[a-z0-9_-]+\\.bbmodel");
    /** Dans le dossier des modèles : l'empreinte de l'archive installée. */
    private static final String MARKER = ".etermob-sync";

    private final JavaPlugin plugin;
    private final String manifestUrl;
    private final long pollTicks;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** Un rechargement de BetterModel n'a pas pu se faire (il était occupé) : retenté au prochain passage. */
    private volatile boolean pendingReload;
    private volatile String lastError;
    private volatile boolean versionWarned;
    /** Ce que /etermob debug affiche : version du pack de modèles installée, dernière vérification. */
    private static volatile String status = "pas encore vérifié";

    public static String status() {
        return status;
    }

    public ModelSync(JavaPlugin plugin, String manifestUrl, int pollMinutes) {
        this.plugin = plugin;
        this.manifestUrl = manifestUrl;
        this.pollTicks = Math.max(1, pollMinutes) * 60L * 20L;
    }

    /** Premier passage 30 s après le démarrage (BetterModel a fini son premier chargement), puis régulièrement. */
    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sync, 30 * 20L, pollTicks);
    }

    private void sync() {
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (betterModel == null || !betterModel.isEnabled()) {
            return;
        }
        try {
            Path folder = betterModel.getDataFolder().toPath();
            boolean reload = pendingReload | fixConfig(folder.resolve("config.yml"));
            JsonObject manifest = JsonParser.parseString(get(manifestUrl)).getAsJsonObject();
            checkVersion(manifest, betterModel);
            JsonElement models = manifest.get("models");
            Path dir = folder.resolve("models");
            Files.createDirectories(dir);
            Path marker = dir.resolve(MARKER);
            String installed = Files.exists(marker) ? Files.readString(marker).trim() : "";
            if (models != null && models.isJsonObject()) {
                JsonObject info = models.getAsJsonObject();
                String sha1 = info.get("sha1").getAsString();
                if (!sha1.equalsIgnoreCase(installed)) {
                    int count = install(dir, info.get("url").getAsString(), sha1);
                    Files.writeString(marker, sha1);
                    plugin.getLogger().info("Modèles : " + count + " modèle(s) installé(s) (version "
                            + (info.has("version") ? info.get("version").getAsString() : "?") + ")");
                    reload = true;
                }
                status = "version " + (info.has("version") ? info.get("version").getAsString() : "?")
                        + (info.has("count") ? " · " + info.get("count").getAsString() + " modèle(s)" : "") + " · " + sha1.substring(0, Math.min(8, sha1.length()))
                        + " · vérifié à " + java.time.LocalTime.now().withNano(0);
            } else if (!installed.isEmpty()) {
                // Plus aucun modèle publié : le dossier géré est vidé
                clear(dir, Set.of());
                Files.deleteIfExists(marker);
                plugin.getLogger().info("Modèles : plus aucun modèle publié, dossier vidé");
                reload = true;
            }
            if (reload) {
                pendingReload = !Models.reload();
                if (pendingReload) {
                    plugin.getLogger().warning("Modèles : BetterModel occupé, rechargement retenté au prochain passage");
                }
            }
            lastError = null;
        } catch (Exception e) {
            // Signalé une fois par erreur différente (pas de spam si le bucket est injoignable un moment)
            String message = e.getClass().getSimpleName() + " : " + e.getMessage();
            if (!message.equals(lastError)) {
                lastError = message;
                plugin.getLogger().warning("Modèles : synchronisation impossible (" + message + "), on garde ceux en place");
            }
        }
    }

    /** Télécharge l'archive, vérifie son empreinte, remplace les modèles du dossier ; renvoie leur nombre. */
    private int install(Path dir, String url, String sha1) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("archive des modèles -> " + response.statusCode());
        }
        byte[] zip = response.body();
        String actual = HexFormat.of().formatHex(sha1(zip));
        if (!actual.equalsIgnoreCase(sha1)) {
            throw new IOException("empreinte de l'archive inattendue (" + actual + ")");
        }
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1);
                if (entry.isDirectory() || !MODEL_FILE.matcher(name).matches()) {
                    continue;
                }
                Path part = dir.resolve(name + ".part");
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                Files.move(part, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                names.add(name);
            }
        }
        clear(dir, names);
        return names.size();
    }

    /** Le dossier est géré par EterMob : tout modèle hors de l'archive (exemples de BetterModel...) est retiré. */
    private static void clear(Path dir, Set<String> keep) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".bbmodel") && !keep.contains(name)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    /** Noms stables dans le pack de BetterModel (sinon ils ne correspondent plus au pack publié) ; true si corrigé. */
    private boolean fixConfig(Path file) {
        if (!Files.exists(file)) {
            return false;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        if (!config.getBoolean("pack.use-obfuscation", true)) {
            return false;
        }
        config.set("pack.use-obfuscation", false);
        try {
            config.save(file.toFile());
            plugin.getLogger().info("Modèles : use-obfuscation mis à false dans la config de BetterModel (noms stables)");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Modèles : impossible de corriger la config de BetterModel (" + e.getMessage() + ")");
            return false;
        }
    }

    /** Le pack est généré par une version précise de BetterModel : une autre sur ce serveur peut ne pas correspondre. */
    private void checkVersion(JsonObject manifest, Plugin betterModel) {
        if (versionWarned || !manifest.has("bettermodel")) {
            return;
        }
        String expected = manifest.get("bettermodel").getAsString();
        String running = betterModel.getPluginMeta().getVersion();
        if (!running.startsWith(expected)) {
            versionWarned = true;
            plugin.getLogger().warning("Modèles : le pack est généré avec BetterModel " + expected + ", ce serveur a "
                    + running + " : mettre la même version (générateur de serveurs)");
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Cache-Control", "no-cache").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("manifeste -> " + response.statusCode());
        }
        return response.body();
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
