plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
    jvm()
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xexpect-actual-classes",
        )
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":cupcake")) // the nav_cupcake sample, shown as one of the demos; also provides platformName()
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
        }
        jvmMain.dependencies {
            implementation(rootProject) // the Ubuntu Touch host: runOnUbuntuTouch { }
            implementation(compose.desktop.linux_arm64)
        }
    }
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("playground.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "PlaygroundMainKt" }
    val jvmMain = kotlin.jvm().compilations["main"]
    from(jvmMain.output.allOutputs)
    from(jvmMain.runtimeDependencyFiles.map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
