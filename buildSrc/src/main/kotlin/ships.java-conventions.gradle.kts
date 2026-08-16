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
    toolVersion = "10.26.1"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configDirectory = rootProject.file("config/checkstyle")
}

tasks.withType<Checkstyle>().configureEach {
    if (name.contains("Test")) {
        configFile = rootProject.file("config/checkstyle/checkstyle-test.xml")
    }
}

pmd {
    toolVersion = "7.16.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
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

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude-filter.xml"))
    reports {
        create("html") {
            required = true
        }
        create("xml") {
            required = false
        }
    }
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
