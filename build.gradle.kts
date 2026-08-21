plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

group = "dev.mintychochip"

val calverDate =
    java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))

val calverPattern = Regex("""\d{4}\.(0[1-9]|1[0-2])\.(0[1-9]|[12]\d|3[01])\.[1-9]\d*""")

fun explicitCalver(propertyName: String): String? {
    val value = (findProperty(propertyName) as String?)?.takeIf { it.isNotBlank() } ?: return null
    if (!value.matches(calverPattern)) {
        throw GradleException(
            "$propertyName must match YYYY.MM.DD.<positive-run-number> " +
                "(for example, -P$propertyName=2026.08.21.1).",
        )
    }
    return value
}

// CI: YYYY.MM.DD.<github_run_number>; local builds: dated -SNAPSHOT.
// releaseVersion remains an alias for existing release workflows.
version =
    explicitCalver("buildVersion")
        ?: explicitCalver("releaseVersion")
        ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.let { "$calverDate.$it" }
        ?: "$calverDate-SNAPSHOT"
