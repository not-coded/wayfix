@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
}

val loader = prop("loom.platform")!!
val minecraft = stonecutter.current.version
val common: Project = requireNotNull(stonecutter.node.sibling("")) {
    "No common project for $project"
}

version = "${common.mod.version}+${common.mod.version_name}-${loader}"
group = "${common.mod.maven_group}.$loader"

base {
    archivesName.set(common.mod.name)
}

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val commonBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    get("developmentFabric").extendsFrom(commonBundle)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:${common.mod.dep("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${common.mod.dep("fabric_loader")}")

    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${common.mod.dep("cloth_config_version")}")
    modImplementation("com.terraformersmc:modmenu:${common.mod.dep("mod_menu_version")}")

    implementation("org.lwjgl:lwjgl-glfw:3.3.2")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
}

loom {
    decompilers {
        get("vineflower").apply { // Adds names to lambdas - useful for mixins
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        isIdeConfigGenerated = true
        runDir = "../../../run"
        vmArgs("-Dmixin.debug.export=true")
    }
}

val target = ">=${common.property("mod.min_target")}- <=${common.property("mod.max_target")}"

tasks.processResources {
    val expandProps = mapOf(
        "version" to version,
        "minecraftVersion" to target,
        "javaVersion" to common.mod.dep("java")
    )

    filesMatching("fabric.mod.json") {
        expand(expandProps)
    }

    inputs.properties(expandProps)
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "dev"
}

java {
    withSourcesJar()

    val javaVersion = if (common.property("deps.java") == "9") JavaVersion.VERSION_1_9 else JavaVersion.VERSION_17

    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
    from(tasks.remapJar.get().archiveFile, tasks.remapSourcesJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.file("libs/${mod.version}/$loader"))
    dependsOn("build")
}