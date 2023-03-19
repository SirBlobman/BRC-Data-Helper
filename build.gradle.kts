val baseVersion = findProperty("version.base") ?: "invalid"
val betaString = ((findProperty("version.beta") ?: "false") as String)
val jenkinsBuildNumber = System.getenv("BUILD_NUMBER") ?: "Unofficial"

val betaBoolean = betaString.toBoolean()
val betaVersion = if (betaBoolean) "Beta-" else ""
val calculatedVersion = "$baseVersion.$betaVersion$jenkinsBuildNumber"

val privateMavenUsername = System.getenv("MAVEN_DEPLOY_USR") ?: property("mavenUsernameSirBlobman")
val privateMavenPassword = System.getenv("MAVEN_DEPLOY_PSW") ?: property("mavenPasswordSirBlobman")

plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://nexus.sirblobman.xyz/public/")
    maven("https://nexus.sirblobman.xyz/proxy-jitpack/")

    maven("https://nexus.sirblobman.xyz/private/") {
        credentials {
            username = (privateMavenUsername as String)
            password = (privateMavenPassword as String)
        }
    }
}

dependencies {
    // Java Dependencies
    compileOnly("org.jetbrains:annotations:24.0.1") // JetBrains Annotations
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT") // Spigot API
    compileOnly("com.mysql:mysql-connector-j:8.0.32") // MySQL Connector

    // Plugin Dependencies
    compileOnly("com.github.sirblobman.api:core:2.7-SNAPSHOT") // BlueSlimeCore
    compileOnly("net.brcdev:PlayerShopGUIPlus:1.28.0") // PlayerShopGUIPlus

    // ShopGUIPlus API
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.0.0") {
        exclude("org.spigotmc", "spigot-api")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    named<Jar>("jar") {
        archiveFileName.set("BRC-Data-Helper-$calculatedVersion.jar")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<Javadoc> {
        options.encoding = "UTF-8"
    }

    processResources {
        val pluginName = (findProperty("bukkit.plugin.name") ?: "") as String
        val pluginPrefix = (findProperty("bukkit.plugin.prefix") ?: "") as String
        val pluginDescription = (findProperty("bukkit.plugin.description") ?: "") as String
        val pluginWebsite = (findProperty("bukkit.plugin.website") ?: "") as String
        val pluginMainClass = (findProperty("bukkit.plugin.main") ?: "") as String

        filesMatching("plugin.yml") {
            expand(mapOf(
                "pluginName" to pluginName,
                "pluginPrefix" to pluginPrefix,
                "pluginDescription" to pluginDescription,
                "pluginWebsite" to pluginWebsite,
                "pluginMainClass" to pluginMainClass,
                "pluginVersion" to calculatedVersion
            ))
        }
    }
}
