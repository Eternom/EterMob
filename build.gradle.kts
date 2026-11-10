plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    // Plugins Eter (EterLib, API des autres plugins) : le jar de leur release GitHub (publiée par la CI à chaque tag)
    ivy {
        url = uri("https://github.com/Eternom/")
        patternLayout { artifact("[module]/releases/download/[revision]/[module]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("com.github.Eternom") }
    }
    // Repli : EterLib publié sur cette machine (`gradlew publishToMavenLocal` dans EterLib)
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    // Socle : états (saignement, étourdissement...), rôles de combat, base, langues, pont des quêtes, classements
    compileOnly("com.github.Eternom:EterLib:1.27.0")
    // Terrains des clans (lu à l'usage) : pas d'apparition ni de bloc temporaire sur un terrain
    compileOnly("com.github.Eternom:EterClan:1.7.0")

    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("com.github.Eternom:EterLib:1.27.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        // Déclarée comme entrée : sinon le cache de Gradle réutilise un plugin.yml avec l'ancienne version
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// Après chaque build, copie le jar dans <eterPluginsDir>/paper et y supprime l'ancienne version de ce plugin.
// eterPluginsDir se règle dans ~/.gradle/gradle.properties (propre à ta machine) : sans lui, rien n'est copié (JitPack...).
val deployPlugin by tasks.registering(Copy::class) {
    description = "Copie le jar dans le dossier de plugins local (eterPluginsDir)"
    // Variables locales à la tâche : le cache de configuration de Gradle refuse les variables du script
    val eterPluginsDir = providers.gradleProperty("eterPluginsDir")
    val enabled = eterPluginsDir.isPresent
    onlyIf { enabled }
    val jarName = tasks.jar.flatMap { it.archiveBaseName }
    from(tasks.jar)
    into(eterPluginsDir.map { "$it/paper" }.orElse(layout.buildDirectory.dir("deploy").map { it.asFile.path }))
    doFirst {
        destinationDir.listFiles { file -> file.name.startsWith(jarName.get() + "-") && file.name.endsWith(".jar") }
            ?.forEach { it.delete() }
    }
}
tasks.build { finalizedBy(deployPlugin) }
