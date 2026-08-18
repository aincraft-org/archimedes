plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

group = "dev.mintychochip"

val releaseVersionPattern =
    Regex("""\d{2}\.([1-9]|1[0-2])\.([1-9]|[12]\d|3[01])\.[1-9]\d*""")
val requestedReleaseVersion = (findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() }
if (requestedReleaseVersion != null && !requestedReleaseVersion.matches(releaseVersionPattern)) {
    throw GradleException(
        "releaseVersion must match YY.M.D.<positive-run-number> " +
            "(for example, -PreleaseVersion=26.8.18.1).",
    )
}
version = requestedReleaseVersion
    ?: (findProperty("archimedes.version") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("ARCHIMEDES_VERSION")?.takeIf { it.isNotBlank() }
    ?: "0.0.0-SNAPSHOT"
