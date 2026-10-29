package com.example.models

enum class AppThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class CursorStyle(val label: String) {
    BLOCK("Block"),
    UNDERLINE("Underline"),
    BAR("Bar")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val terminalFontSize: Float = 14f,
    val terminalFontFamily: String = "JetBrains Mono",
    val defaultPort: Int = 22,
    val defaultUsername: String = "root",
    val autoReconnect: Boolean = false,
    val cursorStyle: CursorStyle = CursorStyle.BLOCK
) {
    companion object {
        val AvailableFonts = listOf(
            "JetBrains Mono",
            "Fira Code",
            "System Monospace"
        )
    }
}
