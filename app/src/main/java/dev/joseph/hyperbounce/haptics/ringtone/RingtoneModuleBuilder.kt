package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds universal flashable Magisk / KernelSU / APatch modules
 * containing custom ringtone audio files and handcrafted 24kHz RTP haptic binaries.
 */
object RingtoneModuleBuilder {

    private const val TAG = "RingtoneModuleBuilder"
    const val MODULE_NAME = "HyperBounce_Haptic_Ringtones_Module.zip"

    data class RingtonePackageItem(
        val presetId: String,
        val title: String,
        val cleanName: String,
        val effectId: Int,
        val durationMs: Long,
        val pipelineName: String
    )

    data class BuildResult(
        val success: Boolean,
        val zipFile: File?,
        val items: List<RingtonePackageItem>,
        val errorMessage: String? = null
    )

    fun hasStoragePermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Previews the hardware Effect ID allocations and file mappings for a set of presets
     * before actual module generation.
     */
    fun previewAllocations(
        context: Context,
        selectedPresets: List<SavedHapticPreset>
    ): List<RingtonePackageItem> {
        val existingMappings = RingtoneRegistry.loadMappings(context)
        var nextId = RingtoneRegistry.getNextAvailableEffectId(context)
        val items = mutableListOf<RingtonePackageItem>()
        for (preset in selectedPresets) {
            val rawTitle = preset.title.ifBlank { "CustomRingtone" }
            val cleanName = rawTitle.filter { it.isLetterOrDigit() }.ifBlank { "CustomRingtone" }
            val existing = existingMappings.find { it.filename.equals("${cleanName}.ogg", ignoreCase = true) }
            val effectId = existing?.effectId ?: nextId++
            val pipeName = preset.pipelines.getOrNull(preset.selectedPipelineIndex)?.pipelineType?.title ?: "Melodic Crisp"
            val dur = (preset.trimEndMs - preset.trimStartMs).let { if (it > 0) it.toLong() else preset.durationMs }
            items.add(
                RingtonePackageItem(
                    presetId = preset.id,
                    title = rawTitle,
                    cleanName = cleanName,
                    effectId = effectId,
                    durationMs = dur,
                    pipelineName = pipeName
                )
            )
        }
        return items
    }

    suspend fun buildModule(
        context: Context,
        selectedPresets: List<SavedHapticPreset>,
        customItems: List<RingtonePackageItem>? = null
    ): BuildResult = withContext(Dispatchers.IO) {
        if (selectedPresets.isEmpty()) {
            return@withContext BuildResult(
                success = false,
                zipFile = null,
                items = emptyList(),
                errorMessage = "No presets selected"
            )
        }

        try {
            // Determine target output zip file in public Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val zipFile = File(downloadsDir, MODULE_NAME)
            if (zipFile.exists()) {
                zipFile.delete()
            }

            // Allocate consecutive Effect IDs starting above existing firmware
            var nextId = RingtoneRegistry.getNextAvailableEffectId(context)
            val packageItems = mutableListOf<RingtonePackageItem>()
            val existingMappings = RingtoneRegistry.loadMappings(context).toMutableList()

            val fos = FileOutputStream(zipFile)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            try {
                // 1. Package individual ringtone audio & haptic binaries
                for (preset in selectedPresets) {
                    val loaded = HapticPresetStorage.loadPresetData(context, preset)
                        ?: continue

                    val customItem = customItems?.find { it.presetId == preset.id }
                    val rawTitle = customItem?.title ?: preset.title.ifBlank { "CustomRingtone" }
                    val cleanName = customItem?.cleanName ?: rawTitle.filter { it.isLetterOrDigit() }.ifBlank { "CustomRingtone" }

                    // Also sync rename back to preset storage if changed
                    if (rawTitle != preset.title) {
                        HapticPresetStorage.renamePreset(context, preset.id, rawTitle)
                    }

                    val existing = existingMappings.find { it.filename.equals("${cleanName}.ogg", ignoreCase = true) }
                    val effectId = customItem?.effectId ?: (existing?.effectId ?: nextId++)
                    val pipeIdx = preset.selectedPipelineIndex.coerceIn(0, loaded.synthesizedHaptics.lastIndex)
                    val haptic = loaded.synthesizedHaptics[pipeIdx]

                    val start = preset.trimStartMs.coerceAtLeast(0)
                    val end = if (preset.trimEndMs > start) preset.trimEndMs else loaded.decodedAudio.durationMs.toInt()
                    val croppedWavBytes = RingtoneManagerHelper.cropAndEncodeWav(loaded.decodedAudio, start, end)
                    val croppedHaptic = RingtoneManagerHelper.cropHaptic(haptic, start, end)

                    // Audio path in module: standard Magisk/KSU overlay path
                    // DO NOT add duplicate entries in product/ as MediaScanner will index twice!
                    val audioEntryName = "system/product/media/audio/ringtones/${cleanName}.ogg"
                    zos.putNextEntry(ZipEntry(audioEntryName))
                    zos.write(croppedWavBytes)
                    zos.closeEntry()

                    // Haptic binary path in module: system/vendor/firmware/<ID>_<CleanName>_P_RTP.bin
                    // cleanName MUST NOT contain underscores so Xiaomi HAL regex matches!
                    val hapticEntryName = "system/vendor/firmware/${effectId}_${cleanName}_P_RTP.bin"
                    zos.putNextEntry(ZipEntry(hapticEntryName))
                    zos.write(croppedHaptic.rtpRawBytes)
                    zos.closeEntry()

                    val item = RingtonePackageItem(
                        presetId = preset.id,
                        title = rawTitle,
                        cleanName = cleanName,
                        effectId = effectId,
                        durationMs = croppedHaptic.durationMs,
                        pipelineName = haptic.pipelineType.title
                    )
                    packageItems.add(item)

                    // Register in persistent mapping
                    existingMappings.removeAll { it.filename.equals("${cleanName}.ogg", ignoreCase = true) }
                    existingMappings.add(
                        RingtoneRegistry.RingtoneMapping(
                            title = rawTitle,
                            filename = "${cleanName}.ogg",
                            effectId = effectId
                        )
                    )
                }

                if (packageItems.isEmpty()) {
                    zos.close()
                    zipFile.delete()
                    return@withContext BuildResult(
                        success = false,
                        zipFile = null,
                        items = emptyList(),
                        errorMessage = "Failed to read audio/haptic data from selected presets"
                    )
                }

                // 2. module.prop
                val modulePropContent = buildString {
                    appendLine("id=hyperbounce_custom_ringtones")
                    appendLine("name=HyperBounce Custom Haptic Ringtones")
                    appendLine("version=v1.1")
                    appendLine("versionCode=101")
                    appendLine("author=JosephMontfort & HyperBounce")
                    appendLine("description=Custom ringtones with precision tactile haptics mapped to hardware linear resonant actuator. Bundles ${packageItems.size} custom ringtone(s).")
                }
                zos.putNextEntry(ZipEntry("module.prop"))
                zos.write(modulePropContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. customize.sh
                val customizeShContent = buildString {
                    appendLine("#!/system/bin/sh")
                    appendLine("SKIPUNZIP=0")
                    appendLine("ui_print \"****************************************\"")
                    appendLine("ui_print \"* HyperBounce Custom Haptic Ringtones  *\"")
                    appendLine("ui_print \"* Native Hardware LRA Integration      *\"")
                    appendLine("ui_print \"****************************************\"")
                    appendLine("ui_print \"- Installing ${packageItems.size} custom ringtone(s):\"")
                    packageItems.forEach { item ->
                        appendLine("ui_print \"  * ${item.title} -> Effect Slot #${item.effectId}\"")
                    }
                    appendLine("ui_print \"- Setting permissions...\"")
                    appendLine("set_perm_recursive \$MODPATH 0 0 0755 0644")
                    appendLine("set_perm_recursive \$MODPATH/system/vendor/firmware 0 0 0755 0644 2>/dev/null || true")
                    appendLine("set_perm_recursive \$MODPATH/system/product/media/audio/ringtones 0 0 0755 0644 2>/dev/null || true")
                    appendLine("# Restart vibrator daemon to reindex firmware files")
                    appendLine("killall vendor.xiaomi.hardware.vibratorfeature.service 2>/dev/null || true")
                    appendLine("ui_print \"- Installation complete. Reboot to activate!\"")
                }
                zos.putNextEntry(ZipEntry("customize.sh"))
                zos.write(customizeShContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4. service.sh (Reseeds vibrator daemon on boot & scans MediaStore)
                val serviceShContent = buildString {
                    appendLine("#!/system/bin/sh")
                    appendLine("until [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do")
                    appendLine("    sleep 2")
                    appendLine("done")
                    appendLine("# Reseed Xiaomi vibratorfeature daemon to scan newly mounted /vendor/firmware/ files")
                    appendLine("killall vendor.xiaomi.hardware.vibratorfeature.service 2>/dev/null || true")
                    appendLine("# Notify MediaStore to index new ringtones cleanly (single broadcast)")
                    packageItems.forEach { item ->
                        appendLine("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file:///product/media/audio/ringtones/${item.cleanName}.ogg\" >/dev/null 2>&1")
                    }
                }
                zos.putNextEntry(ZipEntry("service.sh"))
                zos.write(serviceShContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 5. META-INF/com/google/android/update-binary & updater-script
                val updateBinaryContent = buildString {
                    appendLine("#!/sbin/sh")
                    appendLine("#################")
                    appendLine("# Magisk / KernelSU / APatch Module Package")
                    appendLine("#################")
                    appendLine("umask 022")
                    appendLine("require_new_magisk() { echo \"Please install Magisk or KernelSU\"; exit 1; }")
                    appendLine("[ -z \"\$1\" ] && exit 1")
                    appendLine("MAGISKBIN=/data/adb/magisk")
                    appendLine("[ -d \"\$MAGISKBIN\" ] || MAGISKBIN=/data/adb/ksu/bin")
                    appendLine("[ -f \"\$MAGISKBIN/util_functions.sh\" ] && . \"\$MAGISKBIN/util_functions.sh\"")
                    appendLine("install_module")
                    appendLine("exit 0")
                }
                zos.putNextEntry(ZipEntry("META-INF/com/google/android/update-binary"))
                zos.write(updateBinaryContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("META-INF/com/google/android/updater-script"))
                zos.write("# MAGISK\n".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.finish()
            } finally {
                zos.close()
                fos.close()
            }

            // Save updated mappings
            RingtoneRegistry.saveMappings(context, existingMappings)

            Log.i(TAG, "Module successfully generated at ${zipFile.absolutePath} with ${packageItems.size} ringtones")

            BuildResult(
                success = true,
                zipFile = zipFile,
                items = packageItems,
                errorMessage = null
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build ringtone module zip", t)
            BuildResult(
                success = false,
                zipFile = null,
                items = emptyList(),
                errorMessage = t.message ?: "Unknown error while creating zip"
            )
        }
    }
}
