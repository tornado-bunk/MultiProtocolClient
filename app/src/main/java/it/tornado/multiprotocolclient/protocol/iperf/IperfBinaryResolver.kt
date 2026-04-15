package it.tornado.multiprotocolclient.protocol.iperf

import android.content.Context
import android.os.Build
import java.io.File

class IperfBinaryResolver(private val context: Context) {

    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()

    fun resolve(binaryName: String): String? {
        return resolveFromNativeLibraryDir(binaryName)
    }

    fun diagnostics(binaryName: String): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: "(unknown)"
        val nativeDirEntries = listNativeDirEntries()
        return buildString {
            appendLine("Binary '$binaryName' not found for this device.")
            appendLine("Supported ABIs: ${if (supportedAbis.isEmpty()) "(unknown)" else supportedAbis.joinToString(", ")}")
            appendLine("Checked nativeLibraryDir: $nativeDir")
            appendLine("nativeLibraryDir entries: $nativeDirEntries")
            appendLine("Alternative packaging:")
            appendLine("- app/src/main/jniLibs/<abi>/lib${binaryName}.so")
            append("Note: execution from extracted app files is disabled to avoid Android noexec permission errors.")
        }
    }

    private fun resolveFromNativeLibraryDir(binaryName: String): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        val candidates = listOf(
            File(nativeDir, binaryName),
            File(nativeDir, "lib$binaryName.so"),
            File(nativeDir, "$binaryName.bin")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }?.absolutePath
    }

    private fun listNativeDirEntries(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return "(unknown)"
        val dir = File(nativeDir)
        if (!dir.exists() || !dir.isDirectory) return "(missing or not a directory)"
        val files = dir.listFiles().orEmpty()
        if (files.isEmpty()) return "(empty)"
        return files.joinToString(", ") { "${it.name}[x=${it.canExecute()}]" }
    }
}
