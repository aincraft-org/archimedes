plugins {
    `java-library`
    checkstyle
    pmd
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

group = rootProject.group
version = rootProject.version

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

checkstyle {
    toolVersion = "13.11.0"
    maxWarnings = 0
    isIgnoreFailures = false
    config =
        resources.text.fromUri(
            "https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-13.11.0/src/main/resources/google_checks.xml",
        )
    configDirectory = rootProject.file("config/checkstyle")
    configProperties["org.checkstyle.google.suppressionfilter.config"] =
        rootProject.file("config/checkstyle/checkstyle-suppressions.xml").absolutePath
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

pmd {
    toolVersion = "7.26.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
    ruleSets = emptyList()
}

tasks.withType<Pmd>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.36.1")
        endWithNewline()
    }
}

spotbugs {
    toolVersion.set("4.9.7")
    ignoreFailures.set(false)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude-filter.xml"))
    reports {
        create("html") {
            required = true
        }
        create("xml") {
            required = true
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<Checkstyle>())
    dependsOn(tasks.withType<Pmd>())
    dependsOn(tasks.withType<com.github.spotbugs.snom.SpotBugsTask>())
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}
