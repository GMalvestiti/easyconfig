plugins {
    id("dev.kikugie.stonecutter")
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
    register("generateResources") {
        group = "custom"
        description = "Run datagen for all versions"
        dependsOn(stonecutter.tasks.named("runDatagen") {
            metadata.project.endsWith("fabric")
        })
        dependsOn(stonecutter.tasks.named("runData") {
            metadata.project.endsWith("neoforge")
        })
    }

    register("publishMaven") {
        group = "custom"
        description = "Publish all versions to the Maven repository"

        val isDryRun = project.findProperty("publish.dry_run")?.toString()?.toBoolean() ?: true

        if (isDryRun) {
            dependsOn(stonecutter.tasks.named("publishToMavenLocal"))
        } else {
            dependsOn(stonecutter.tasks.named("publishToMavenCentral"))
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
