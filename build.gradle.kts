import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.polaris2023.mcmeta.extension.McMetaSettings
import org.polaris2023.mcmeta.extension.forge.ForgeLikeDependency
import org.polaris2023.mcmeta.extension.forge.ForgeLikeToml
import org.polaris2023.mcmeta.extension.forge.neo.NeoForgeDependency
import org.polaris2023.mcmeta.extension.forge.neo.NeoForgeMods
import org.polaris2023.mcmeta.extension.forge.neo.NeoForgeModsToml
import org.slf4j.event.Level

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.github.polari-stars-mc:mcmeta-plugin:0.0.4-fix.1")
    }
}


plugins {
    `java-library`
    `maven-publish`
    idea
    id("net.neoforged.moddev").version("2.+")
    id("io.github.jeadyx.sonatype-uploader").version("2.+")
}

apply(plugin = "io.github.Polari-Stars-MC.mcmeta-plugin")

configure<McMetaSettings> {
    loaderType = McMetaSettings.Type.NEOFORGE
}



val parchmentMcVersion: String by rootProject
val parchmentMappingVersion: String by rootProject
val mcVersion: String by rootProject
val modVersion: String by rootProject
val mavenGroup: String by rootProject
val modid: String by rootProject
val modName: String by rootProject
val modDesc: String by rootProject
val authorsList: String by rootProject
val neoVersion: String by  rootProject
val neoVersionRange = "[$neoVersion,)"
val loaderVersionRange: String by rootProject
val projLicense: String by rootProject
val githubRep: String by rootProject
val mcVersionRange: String by rootProject

configure<ForgeLikeToml> {
    loaderVersion = loaderVersionRange
    license = projLicense
    issueTrackerURL = uri("https://github.com/$githubRep/issues")
}

tasks.named<Wrapper>("wrapper").configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = modVersion
group = mavenGroup

repositories {
    mavenLocal()
}

base {
    archivesName = modid
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

val atFile = file("src/main/resources/META-INF/accesstransformer.cfg")
val eeFile = file("src/main/resources/META-INF/enumExtensions.cfg")
val dataDir = file("src/generated/resources")
val resDir = file("src/main/resources")
atFile.parentFile.mkdirs()
atFile.createNewFile()
eeFile.createNewFile()
dataDir.mkdirs()
resDir.mkdirs()
neoForge {
    version = neoVersion
    parchment {
        minecraftVersion = parchmentMcVersion
        mappingsVersion = parchmentMappingVersion
    }
    if (atFile.parentFile.exists()  && atFile.readBytes().isNotEmpty()) {
        setAccessTransformers(atFile)
    }
    runs {
        register("client") {
            client()
            gameDirectory = file("run/client")
            systemProperty("neoforge.enabledGameTestNamespaces", modid)
        }
        register("server") {
            server()
            gameDirectory = file("run/server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modid)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run/test")
            systemProperty("neoforge.enabledGameTestNamespaces", modid)
        }
        register("runData") {
            clientData()
            gameDirectory = file("run/data")
            programArguments.addAll(
                "--mod", modid, "--all",
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
        register(modid) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets {
    main {
        resources {
            srcDir(dataDir.absolutePath)
            srcDir("build/generated/modMetaData")
        }
    }
}

val localRuntime by configurations.registering
val runtimeClasspath: Configuration by configurations.getting
runtimeClasspath.extendsFrom(localRuntime.get())

configure<NeoForgeModsToml> {
    mods.add(NeoForgeMods(project).apply {
        this.modId = modid
        namespace = modid
        version = modVersion
        displayName = modName
        description = modDesc
        authors = authorsList
        logoFile = "$modid.png"
        if (eeFile.readBytes().isNotEmpty()) {
            enumExtensions = "META-INF/enumExtensions.json"
        }
    })
    dependencies().put(modid, arrayOf(
        NeoForgeDependency
            .builder()
            .type(NeoForgeDependency.Type.required)
            .like(
                ForgeLikeDependency
                .builder()
                .modId("neoforge")
                .versionRange(neoVersionRange)
                .ordering(ForgeLikeDependency.Order.NONE)
                .side(ForgeLikeDependency.Side.BOTH)
                .build())
            .build(),
        NeoForgeDependency
            .builder()
            .type(NeoForgeDependency.Type.required)
            .like(ForgeLikeDependency
                .builder()
                .modId("minecraft")
                .versionRange(mcVersionRange)
                .ordering(ForgeLikeDependency.Order.NONE)
                .side(ForgeLikeDependency.Side.BOTH)
                .build())
            .build()
    ))
    mixins().add("META-INF/$modid.mixins.json")
    if (atFile.readBytes().isNotEmpty()) {
        accessTransformers().add("META-INF/accesstransformer.cfg")
    }
}

val mixinsFile = file("src/main/resources/META-INF/$modid.mixins.json")

val gson = GsonBuilder().setPrettyPrinting().create()

if (mixinsFile.exists().not()) {
    mixinsFile.parentFile.mkdirs()
    val mixinJson = JsonObject().apply {
        addProperty("required", true)
        addProperty("package", "org.pusvolans.$modid.mixin")
        addProperty("compatibilityLevel", "JAVA_21")
        addProperty("refmap", "$modid.remap.json")
        add("mixins", JsonArray())
        add("client", JsonArray())
        add("server", JsonArray())
        addProperty("minVersion", "0.8")

    }
    mixinsFile.bufferedWriter(Charsets.UTF_8).use {
        gson.toJson(mixinJson, it)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    if (atFile.readBytes().isEmpty()) {
        exclude("META-INF/accesstransformer.cfg")
    }
    if (eeFile.readBytes().isEmpty()) {
        exclude("META-INF/enumExtensions.json")
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

sonatypeUploader {
    tokenName = properties["central.sonatype.token.name"].toString()
    tokenPasswd = properties["central.sonatype.token.passwd"].toString()
    signing = Action {
        this.keyId = properties["signing.key.id"].toString()
        this.keyPasswd = properties["signing.key.passwd"].toString()
        this.secretKeyPath = properties["signing.secret.key"].toString()
    }
    pom = Action {
        name.set(modName)
        description.set(modDesc)
        url.set("https://github.com/$githubRep")
        licenses {
            license {
                name.set("MIT")
                url = "https://mit-license.org/"
            }
        }
        developers {
            developer {
                id.set("baka4n")
                name.set("baka4n")
                email.set("474899581@qq.com")
            }//sign authors
        }
        scm {
            connection = "scm:git:git://github.com/$githubRep.git"
            developerConnection = "scm:git:ssh://github.com/$githubRep.git"
            url = "https://github.com/$githubRep"
        }
    }
}