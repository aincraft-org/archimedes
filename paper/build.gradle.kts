import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    id("ships.java-conventions")
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper")
}

val paperVersion = providers.gradleProperty("paper_version").orElse("26.2")
val paperDevBundleVersion = providers.gradleProperty("paper_dev_bundle").orElse("26.2.build.+")

dependencies {
    implementation(project(":common"))
    paperweight.paperDevBundle(paperDevBundleVersion.get())
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}

fun Project.mainOutput() =
    extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output }

tasks.jar {
    from(project(":api").mainOutput())
    from(project(":common").mainOutput())
    archiveFileName.set("ships-${project.version}.jar")
}

tasks {
    runServer {
        minecraftVersion(paperVersion.get())
        runDirectory.set(rootProject.layout.projectDirectory.dir("run"))
    }
}

// Paper 26.2 uses Mojang-mapped runtime classes, so the normal artifact is
// already suitable for the Paper server launched by run-paper.
paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}
