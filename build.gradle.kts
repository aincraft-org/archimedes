plugins {
    java
    checkstyle
    pmd
    id("com.diffplug.spotless") version "8.9.0"
    id("com.github.spotbugs") version "6.5.10"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
}

pmd {
    toolVersion = "7.16.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSetFiles = files("config/pmd/pmd-ruleset.xml")
    ruleSets = emptyList()
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.27.0")
        endWithNewline()
    }
}

spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = false
}
tasks.jar {
    archiveFileName.set("ships-${project.version}.jar")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports {
        create("html") {
            required = true
        }
        create("xml") {
            required = false
        }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

group = "dev.jlo"
version = "0.1.0-SNAPSHOT"

val paperVersion = providers.gradleProperty("paper_version").orElse("26.2")
val paperDevBundleVersion = providers.gradleProperty("paper_dev_bundle").orElse("26.2.build.+")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(paperDevBundleVersion.get())
}

tasks {
    runServer {
        minecraftVersion(paperVersion.get())
    }
}

// Paper 26.2 uses Mojang-mapped runtime classes, so the normal artifact is
// already suitable for the Paper server launched by run-paper.
paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}
