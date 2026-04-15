package it.tornado.multiprotocolclient.settings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class UiTextSize(val scaleFactor: Float) {
    SMALL(0.92f),
    MEDIUM(1.0f),
    LARGE(1.12f)
}

enum class ConsoleFontSize {
    SMALL,
    MEDIUM,
    LARGE
}

enum class LogExportFormat {
    TXT,
    JSON
}

data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val uiTextSize: UiTextSize = UiTextSize.MEDIUM,
    val consoleAutoScroll: Boolean = true,
    val consoleShowTimestamps: Boolean = false,
    val consoleFontSize: ConsoleFontSize = ConsoleFontSize.MEDIUM,
    val consoleBufferLimit: Int = 1000,
    val confirmBeforeReset: Boolean = true,
    val keepLastFieldsPerProtocol: Boolean = false,
    val defaultTimeoutSeconds: Int = 8,
    val showLocalNetworkWarnings: Boolean = true,
    val maskSensitiveOutput: Boolean = true,
    val clearOutputOnExit: Boolean = false
)
