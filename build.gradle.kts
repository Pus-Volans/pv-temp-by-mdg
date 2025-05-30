import org.slf4j.event.Level

plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev").version("2.+")
}


val parchmentMcVersion: String by rootProject
val parchmentMappingVersion: String by rootProject
val mcVersion: String by rootProject
val modVersion: String by rootProject
val mavenGroup: String by rootProject
val modId: String by rootProject
val neoVersion: String by  rootProject
val neoVersionRange = "[$neoVersion,)"
val loaderVersionRange: String by rootProject
val projLicense: String by rootProject

tasks.named<Wrapper>("wrapper").configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = modVersion
group = mavenGroup

repositories {
    mavenLocal()
}

base {
    archivesName = modId
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

val atFile = file("src/main/resources/META-INF/accesstransformer.cfg")
val dataDir = file("src/generated/resources")
val resDir = file("src/main/resources")
atFile.parentFile.mkdirs()
atFile.createNewFile()
dataDir.mkdirs()
resDir.mkdirs()
neoForge {
    version = neoVersion
    parchment {
        minecraftVersion = parchmentMcVersion
        mappingsVersion = parchmentMappingVersion
    }
    if (atFile.readBytes().isNotEmpty()) {
        accessTransformers.from(atFile)
    }
    runs {
        register("client") {
            client()
            gameDirectory = file("run/client")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("server") {
            server()
            gameDirectory = file("run/server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run/test")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("runData") {
            clientData()
            gameDirectory = file("run/data")
            programArguments.addAll(
                "--mod", modId, "--all",
                "--output", dataDir.absolutePath,
                "--existing", resDir.absolutePath
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets {
    main {
        resources {
            srcDir(atFile.absolutePath)
        }
    }
}

val localRuntime by configurations.registering
val runtimeClasspath by configurations.getting
runtimeClasspath.extendsFrom(localRuntime.get())