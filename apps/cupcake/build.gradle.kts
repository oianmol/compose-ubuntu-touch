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
            "-opt-in=org.jetbrains.compose.resources.ExperimentalResourceApi",
            "-Xexpect-actual-classes",
        )
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
        jvmMain.dependencies {
            implementation(rootProject) // the Ubuntu Touch host: runOnUbuntuTouch { }
            implementation(compose.desktop.linux_arm64)
        }
    }
}

compose.resources {
    packageOfResClass = "cupcake.composeapp.generated.resources" // what the example's imports expect
    generateResClass = always
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("cupcake.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "MainKt" }
    val jvmMain = kotlin.jvm().compilations["main"]
    from(jvmMain.output.allOutputs)
    from(jvmMain.runtimeDependencyFiles.map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
