plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

kotlin {
    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.addAll("-opt-in=androidx.compose.ui.InternalComposeUiApi", "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi") }
}

dependencies {
    // Natives for the phone (Linux aarch64), not the build machine.
    implementation(compose.desktop.linux_arm64)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
}

// One jar with every dependency, so the phone only needs a JDK.
tasks.register<Jar>("fatJar") {
    archiveFileName.set("compose-spike.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "MainKt" }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
