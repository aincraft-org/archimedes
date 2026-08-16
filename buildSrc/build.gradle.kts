plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.9.0")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.10")
}
