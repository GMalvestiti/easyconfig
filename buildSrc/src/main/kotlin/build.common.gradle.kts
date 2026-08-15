import com.vanniktech.maven.publish.Checksum
import com.vanniktech.maven.publish.JavadocJar
import me.modmuss50.mpp.ReleaseType

plugins {
    java
    idea
    jacoco
    id("com.gradleup.shadow")
    id("com.vanniktech.maven.publish")
    id("me.modmuss50.mod-publish-plugin")
}

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url], improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    mavenLocal()
    mavenCentral()
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

configurations {
    named("implementation") {
        extendsFrom(configurations.shadow.get())
    }
}

fun prop(property: String): String {
    return project.property(property) as String
}

fun getChangelog(isModrinth: Boolean): String {
    if (isModrinth
        && prop("publish.modrinth_changelog").isNotBlank()
        && (prop("publish.start") != prop("publish.modrinth_changelog"))) {
        return ""
    }

    val targetHeader = "# ${prop("mod.version")}"

    return rootProject.file("CHANGELOG.md").useLines { lines ->
        lines.dropWhile { !it.trim().contains(targetHeader, ignoreCase = true) }
            .drop(1)
            .takeWhile { !it.trim().startsWith("# ") }
            .joinToString("\n")
            .trim()
    }
}

fun getReleaseType(): ReleaseType = when (prop("publish.type").trim().lowercase()) {
    "alpha" -> ReleaseType.ALPHA
    "beta" -> ReleaseType.BETA
    else -> ReleaseType.STABLE
}

val shadowGroup: String = "${prop("mod.group")}.${prop("mod.id")}.shadow"

val mockitoAgent = configurations.create("mockitoAgent")

val rootPackage = "${prop("mod.group")}.${prop("mod.id")}"

val excludedPackages = listOf(
    "${rootPackage}.datagen.*",
    "${rootPackage}.mixin.*",
    "${rootPackage}.platform.fabric.*",
    "${rootPackage}.platform.neoforge.*"
)

dependencies {
    shadow("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")

    testImplementation(platform("org.junit:junit-bom:${prop("deps.junit")}"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.mockito:mockito-core:${prop("deps.mockito")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${prop("deps.mockito")}")

    mockitoAgent("org.mockito:mockito-core:${prop("deps.mockito")}") {
        isTransitive = false
    }
}

tasks {
    register<Copy>("installGitHooks") {
        group = "help"
        description = "Installs git hooks for the project"

        from(File(rootProject.rootDir, "scripts/pre-commit"))
        into(File(rootProject.rootDir, ".git/hooks"))

        filePermissions {
            unix("rwxr-xr-x")
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    withType<Javadoc>().configureEach {

        exclude("**/*")

        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            docEncoding = "UTF-8"
            charSet = "UTF-8"

            addBooleanOption("Xdoclint:all,-missing", true)

            links("https://docs.oracle.com/en/java/javase/25/docs/api/")
        }
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        dependsOn(jar)

        archiveClassifier.set("")

        from(zipTree(jar.flatMap { it.archiveFile }))

        configurations = listOf(project.configurations.shadow.get())

        relocate("com.github.benmanes.caffeine", "${shadowGroup}.caffeine")
        relocate("com.google.errorprone", "${shadowGroup}.errorprone")
        relocate("org.jspecify", "${shadowGroup}.jspecify")

        mergeServiceFiles()
        addMultiReleaseAttribute.set(false)

        exclude("META-INF/LICENSE", "META-INF/maven/**", "META-INF/versions/**/OSGI-INF/**")
    }

    assemble {
        dependsOn(shadowJar)
    }

    test {
        workingDir = project.layout.projectDirectory.dir("runTest").asFile

        useJUnitPlatform()

        // https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
        jvmArgs("-javaagent:${mockitoAgent.asPath}")

        doFirst {
            workingDir.mkdirs()
        }

        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)

        classDirectories.setFrom(classDirectories.files.map {
            fileTree(it).matching {
                exclude(excludedPackages.map { pkg -> "**/$pkg"
                    .replace('.', '/')
                    .replace("/*", "/**")
                })
            }
        })

        reports {
            xml.required.set(true)
            csv.required.set(false)
            html.required.set(true)
        }
    }
}

afterEvaluate {
    mavenPublishing {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()

        coordinates(
            prop("mod.group"),
            project.base.archivesName.get(),
            project.version.toString()
        )

        if (!prop("dev.javadoc").toBoolean()) {
            configureBasedOnAppliedPlugins(
                javadocJar = JavadocJar.Empty()
            )
        }

        pom {
            name.set(prop("mod.name"))
            description.set(prop("mod.description"))
            inceptionYear.set("2026")
            url.set(prop("mod.contact_sources"))

            licenses {
                license {
                    name.set(prop("mod.license"))
                    url.set("${prop("mod.contact_sources")}/blob/main/LICENSE")
                    distribution.set("${prop("mod.contact_sources")}/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set(prop("mod.author"))
                    name.set(prop("mod.author"))
                    url.set("https://github.com/${prop("mod.author")}")
                }
            }

            scm {
                url.set(prop("mod.contact_sources"))
                connection.set("scm:git:git://github.com/${prop("mod.author")}/${prop("mod.id")}.git")
                developerConnection.set("scm:git:ssh://git@github.com/${prop("mod.author")}/${prop("mod.id")}.git")
            }
        }

        checksums(Checksum.MD5, Checksum.SHA1, Checksum.SHA256, Checksum.SHA512)
        excludeSignatureChecksums(false)
    }

    publishMods {
        version.set(project.version.toString())
        type.set(getReleaseType())

        modrinth {
            changelog.set(getChangelog(true))
            accessToken.set(providers.gradleProperty("modrinth.token"))
            projectId.set(prop("publish.modrinth_id"))

            minecraftVersionRange {
                start.set(prop("publish.start"))
                end.set(prop("publish.end"))
            }

            environment.set(CLIENT_AND_SERVER)
            projectDescription.set(providers.fileContents(layout.projectDirectory.file("README.md")).asText)
        }

        curseforge {
            changelog.set(getChangelog(false))
            accessToken.set(providers.gradleProperty("curseforge.token"))
            projectId.set(prop("publish.curseforge_id"))

            minecraftVersionRange {
                start.set(prop("publish.start"))
                end.set(prop("publish.end"))
            }

            client.set(prop("publish.curseforge_client").toBoolean())
            server.set(prop("publish.curseforge_server").toBoolean())
            projectSlug.set(prop("publish.curseforge_slug"))
            changelogType.set("markdown")
        }

        dryRun.set(prop("publish.dry_run").toBoolean())
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
