plugins {
    id("ships.java-conventions")
}

dependencies {
    api(project(":api"))
    implementation("com.google.code.gson:gson:2.13.1")
}
