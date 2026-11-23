// Entry point for Ubuntu Touch: runs the example's shared App() through the Qt host instead of a desktop window.
fun main() {
    println("cupcake on ${platformName()}")
    runOnUbuntuTouch { App() }
}
