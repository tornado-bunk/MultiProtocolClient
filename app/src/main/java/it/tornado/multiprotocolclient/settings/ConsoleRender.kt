package it.tornado.multiprotocolclient.settings

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val sensitiveRegex = Regex("(?i)(password|token|secret|api[_-]?key)\\s*[:=]\\s*([^\\s,;]+)")
private val timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

fun renderConsoleLines(
    rawLines: List<String>,
    settings: UiSettings
): List<String> {
    val limited = rawLines.takeLast(settings.consoleBufferLimit.coerceIn(100, 20000))
    val withMask = if (settings.maskSensitiveOutput) {
        limited.map { line ->
            sensitiveRegex.replace(line) { match ->
                "${match.groupValues[1]}: ***"
            }
        }
    } else {
        limited
    }
    if (!settings.consoleShowTimestamps) return withMask
    val stamp = LocalTime.now().format(timestampFormatter)
    return withMask.map { "[$stamp] $it" }
}
