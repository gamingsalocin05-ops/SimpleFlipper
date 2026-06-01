plugins {
    id("fabric-loom") version "1.15-SNAPSHOT"
    java
}

val modId = property("mod_id").toString()
val modVersion = property("mod_version").toString()
val mavenGroup = property("maven_group").toString()
val minecraftVersion = property("minecraft_version").toString()
val loaderVersion = property("loader_version").toString()

group = mavenGroup
version = modVersion
base { archivesName.set(modId) }

loom {
    accessWidenerPath.set(file("src/main/resources/simpleflipper.accesswidener"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", modVersion)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion
        )
    }
}
