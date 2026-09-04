package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RingtoneHelper {

    private const val TAG = "RingtoneHelper"

    /**
     * Checks if the app has permission to write system settings (required to change ringtones).
     */
    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    /**
     * Creates an intent to request the WRITE_SETTINGS permission in system settings.
     */
    fun getWriteSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    sealed class RingtoneTarget(val type: Int, val label: String) {
        object PhoneRingtone : RingtoneTarget(RingtoneManager.TYPE_RINGTONE, "Phone Ringtone")
        object Notification : RingtoneTarget(RingtoneManager.TYPE_NOTIFICATION, "Notification Sound")
        object Alarm : RingtoneTarget(RingtoneManager.TYPE_ALARM, "Alarm Sound")
    }

    /**
     * Trims the audio track between startSec and stopSec and sets it as the default ringtone/notification/alarm.
     */
    suspend fun setTrackAsRingtone(
        context: Context,
        track: AudioTrack,
        startSec: Int,
        stopSec: Int,
        target: RingtoneTarget = RingtoneTarget.PhoneRingtone
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!canWriteSettings(context)) {
                return@withContext Result.failure(
                    SecurityException("Permission to modify system settings is required.")
                )
            }

            val sanitizedTitle = track.displayTitle.take(30).replace("[^a-zA-Z0-9 _-]".toRegex(), "")
            val ringtoneTitle = if (startSec > 0 || stopSec * 1000L < track.durationMs) {
                "${sanitizedTitle}_${startSec}s-${stopSec}s"
            } else {
                "${sanitizedTitle}_Ringtone"
            }

            val ringtonesDir = File(context.cacheDir, "ringtones").apply { mkdirs() }
            val isTrimmed = startSec > 0 || (stopSec * 1000L < track.durationMs && track.durationMs > 0)

            val ringtoneFile: File
            val mimeType: String

            if (isTrimmed) {
                val trimmedWavFile = File(ringtonesDir, "${ringtoneTitle}_${System.currentTimeMillis()}.wav")
                val trimSuccess = trimAudioToWav(
                    context = context,
                    sourceUri = track.uri,
                    startSec = startSec,
                    stopSec = stopSec,
                    outputFile = trimmedWavFile
                )

                if (trimSuccess && trimmedWavFile.exists() && trimmedWavFile.length() > 1024) {
                    ringtoneFile = trimmedWavFile
                    mimeType = "audio/wav"
                } else {
                    // Fallback to original audio if trimming failed
                    val fallbackFile = File(ringtonesDir, "${ringtoneTitle}_full.mp3")
                    copySourceToFile(context, track.uri, fallbackFile)
                    ringtoneFile = fallbackFile
                    mimeType = "audio/mpeg"
                }
            } else {
                val copyFile = File(ringtonesDir, "${ringtoneTitle}_full.mp3")
                copySourceToFile(context, track.uri, copyFile)
                ringtoneFile = copyFile
                mimeType = "audio/mpeg"
            }

            if (!ringtoneFile.exists() || ringtoneFile.length() <= 0) {
                return@withContext Result.failure(Exception("Could not extract ringtone audio file."))
            }

            // Save into Android MediaStore Ringtones collection
            val ringtoneUri = insertIntoRingtonesMediaStore(
                context = context,
                file = ringtoneFile,
                title = ringtoneTitle,
                mimeType = mimeType,
                target = target
            ) ?: return@withContext Result.failure(Exception("Failed to register ringtone with MediaStore."))

            // Set as the active default ringtone
            RingtoneManager.setActualDefaultRingtoneUri(context, target.type, ringtoneUri)
            Log.i(TAG, "Successfully set default ${target.label} to $ringtoneUri")

            Result.success("Set as ${target.label} successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ringtone", e)
            Result.failure(e)
        }
    }

    private fun copySourceToFile(context: Context, sourceUri: Uri, destination: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Decodes and trims audio between startSec and stopSec using MediaExtractor and MediaCodec into a PCM WAV file.
     */
    private fun trimAudioToWav(
        context: Context,
        sourceUri: Uri,
        startSec: Int,
        stopSec: Int,
        outputFile: File
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var outputStream: FileOutputStream? = null

        return try {
            extractor = MediaExtractor().apply {
                setDataSource(context, sourceUri, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val startUs = startSec * 1_000_000L
            val stopUs = stopSec * 1_000_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            outputStream = FileOutputStream(outputFile)
            // Placeholder 44-byte WAV header
            outputStream.write(ByteArray(44))

            var totalAudioLen = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 5000L

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > stopUs) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        // Only write samples if buffer presentation timestamp is >= startUs
                        if (bufferInfo.presentationTimeUs >= startUs - 100_000L) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(chunk, 0, bufferInfo.size)
                            outputStream.write(chunk)
                            totalAudioLen += bufferInfo.size
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || bufferInfo.presentationTimeUs > stopUs) {
                        isOutputEOS = true
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            // Write real WAV header with total audio length
            updateWavHeader(outputFile, totalAudioLen, sampleRate, channelCount)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming audio to WAV", e)
            false
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Rewrites standard 44-byte RIFF WAV header into the output file.
     */
    private fun updateWavHeader(file: File, totalAudioLen: Long, sampleRate: Int, channels: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2 // 16-bit = 2 bytes per sample

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort()) // Block align
            putShort(16.toShort()) // Bits per sample
            put("data".toByteArray())
            putInt(totalAudioLen.toInt())
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    /**
     * Inserts the audio into the system MediaStore ringtones catalog.
     */
    private fun insertIntoRingtonesMediaStore(
        context: Context,
        file: File,
        title: String,
        mimeType: String,
        target: RingtoneTarget
    ): Uri? {
        val resolver = context.contentResolver
        val fileName = file.name

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_RINGTONE, target == RingtoneTarget.PhoneRingtone)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, target == RingtoneTarget.Notification)
            put(MediaStore.Audio.Media.IS_ALARM, target == RingtoneTarget.Alarm)
            put(MediaStore.Audio.Media.IS_MUSIC, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/Loopify")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy ringtone bytes to MediaStore", e)
            return null
        }
    }
}
