# Cupcake (example app, unmodified)

Source: JetBrains/compose-multiplatform, tag v1.8.0, `examples/nav_cupcake/composeApp/src/commonMain` (Apache-2.0, see
LICENSE.txt). The Kotlin and resource files are copied unchanged; only `main.kt` is ours and calls `runOnUbuntuTouch { App() }`.
The upstream build targets Android, iOS, web and desktop; here the common code is compiled as plain JVM.
