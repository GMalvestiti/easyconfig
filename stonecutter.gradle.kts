import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("dev.kikugie.stonecutter")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

stonecutter active "26.1-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Makes version- and loader-specific properties apply from `stonecutter.properties.toml`
    properties {
        tags(version, loader)
    }

    // Adds constants to Stonecutter comments (i.e. for `//? if fabric {...`)
    constants {
        match(loader, "fabric", "neoforge")
    }

    swaps["mod_id"] = "\"${properties.get<String>("mod.id")}\";"
    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"
    constants["debug"] = properties.get<String>("dev.debug").toBoolean()

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
            replace("FabricDataOutput", "FabricPackOutput")
            replace("FabricTagProvider", "FabricTagsProvider")
        }
    }
}

tasks {
    register<Exec>("publishMaven") {
        group = "custom"
        description = "Publish all versions to the Maven repository"

        val isDryRun = project.findProperty("publish.dry_run")?.toString()?.toBoolean() ?: true

        if (isDryRun) {
            dependsOn(stonecutter.tasks.named("publishToMavenLocal"))
        } else {
            val autoRelease = project.findProperty("publish.auto_release")?.toString()?.toBoolean() ?: false
            val closeTask = if (autoRelease) "closeAndReleaseSonatypeStagingRepository" else "closeSonatypeStagingRepository"

            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                commandLine("cmd", "/c", "gradlew.bat", "publishToSonatype", closeTask, "--no-configuration-cache")
            } else {
                commandLine("./gradlew", "publishToSonatype", closeTask, "--no-configuration-cache")
            }
        }
    }

    register("publishMod") {
        group = "custom"
        description = "Publish all versions to the mod repositories"

        dependsOn(stonecutter.tasks.named("publishMods"))
    }

    register("runActiveClient") {
        group = "custom"
        description = "Run client of the active Stonecutter version"
        dependsOn(stonecutter.current!!.project + ":runClient")
    }

    register("runActiveServer") {
        group = "custom"
        description = "Run server of the active Stonecutter version"
        dependsOn(stonecutter.current!!.project + ":runServer")
    }

    register("runActiveTest") {
        group = "custom"
        description = "Run test of the active Stonecutter version"
        dependsOn(stonecutter.current!!.project + ":test")
    }
}

nexusPublishing {

    packageGroup.set("${properties["mod.group"]}")

    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(providers.gradleProperty("sonatypeUsername").orNull)
            password.set(providers.gradleProperty("sonatypePassword").orNull)
        }
    }
}
