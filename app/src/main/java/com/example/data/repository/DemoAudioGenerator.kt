package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object DemoAudioGenerator {

    private const val SAMPLE_RATE = 44100
    private const val NUM_CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    suspend fun getOrGenerateDemoTracks(context: Context): List<AudioTrack> = withContext(Dispatchers.IO) {
        val demoDir = File(context.filesDir, "demo_audios")
        if (!demoDir.exists()) {
            demoDir.mkdirs()
        }

        val track1File = File(demoDir, "demo_acoustic_melody.wav")
        val track2File = File(demoDir, "demo_lofi_focus.wav")

        if (!track1File.exists() || track1File.length() < 1000) {
            generateMelodicAcousticTrack(track1File)
        }

        if (!track2File.exists() || track2File.length() < 1000) {
            generateLoFiFocusTrack(track2File)
        }

        val tracks = mutableListOf<AudioTrack>()

        if (track1File.exists()) {
            tracks.add(
                AudioTrack(
                    id = -101L,
                    uri = Uri.fromFile(track1File),
                    title = "Acoustic Melody Loop (Demo)",
                    artist = "LoopCount",
                    album = "Built-in Demos",
                    durationMs = 16000L,
                    folderName = "Built-in Demos",
                    albumId = -1L,
                    dateAdded = System.currentTimeMillis() / 1000,
                    relativePath = "Built-in Demos/"
                )
            )
        }

        if (track2File.exists()) {
            tracks.add(
                AudioTrack(
                    id = -102L,
                    uri = Uri.fromFile(track2File),
                    title = "Lo-Fi Focus Chords (Demo)",
                    artist = "LoopCount",
                    album = "Built-in Demos",
                    durationMs = 20000L,
                    folderName = "Built-in Demos",
                    albumId = -1L,
                    dateAdded = System.currentTimeMillis() / 1000,
                    relativePath = "Built-in Demos/"
                )
            )
        }

        tracks
    }

    /**
     * Synthesizes a 16-second loop with C major -> G -> Am -> F chord progression with bell chimes.
     */
    private fun generateMelodicAcousticTrack(outputFile: File) {
        val durationSeconds = 16
        val totalSamples = SAMPLE_RATE * durationSeconds
        val pcmData = ShortArray(totalSamples)

        // Note frequencies (Hz)
        val noteC3 = 130.81
        val noteG3 = 196.00
        val noteA3 = 220.00
        val noteF3 = 174.61

        val noteC4 = 261.63
        val noteD4 = 293.66
        val noteE4 = 329.63
        val noteG4 = 392.00
        val noteA4 = 440.00
        val noteB4 = 493.88
        val noteC5 = 523.25
        val noteE5 = 659.25

        val chordBars = listOf(
            listOf(noteC3, noteC4, noteE4, noteG4), // C
            listOf(noteG3, noteB4 / 2.0, noteD4, noteG4), // G
            listOf(noteA3, noteC4, noteE4, noteA4), // Am
            listOf(noteF3, noteC4, noteF3 * 2.0, noteA4)  // F
        )

        val melodyNotes = listOf(
            noteE5, noteG4, noteC5, noteD4,
            noteD4 * 2.0, noteB4, noteG4, noteD4,
            noteC5, noteE5, noteA4, noteE4,
            noteA4, noteC5, noteE5, noteC5
        )

        val barDurationSamples = totalSamples / 4
        val melodyNoteDuration = totalSamples / melodyNotes.size

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val barIndex = (i / barDurationSamples).coerceIn(0, 3)
            val currentChord = chordBars[barIndex]

            // Bass & Chord synthesis (soft organ/pad)
            var chordSample = 0.0
            for (noteFreq in currentChord) {
                val fundamental = sin(2.0 * PI * noteFreq * t)
                val harmonic = 0.3 * sin(4.0 * PI * noteFreq * t)
                val sub = 0.4 * sin(1.0 * PI * noteFreq * t)
                chordSample += (fundamental + harmonic + sub) / currentChord.size
            }

            // Melody bell chime synthesis
            val melodyIndex = (i / melodyNoteDuration).coerceIn(0, melodyNotes.size - 1)
            val noteSampleTime = (i % melodyNoteDuration).toDouble() / SAMPLE_RATE
            val noteFreq = melodyNotes[melodyIndex]

            // Bell decay envelope
            val env = exp(-noteSampleTime * 4.5)
            val bellSample = env * (
                    0.6 * sin(2.0 * PI * noteFreq * noteSampleTime) +
                            0.3 * sin(2.0 * PI * noteFreq * 2.0 * noteSampleTime) +
                            0.15 * sin(2.0 * PI * noteFreq * 3.01 * noteSampleTime)
                    )

            // Gentle pulse rhythm
            val pulseT = (i % (SAMPLE_RATE / 2)).toDouble() / SAMPLE_RATE
            val pulseEnv = exp(-pulseT * 12.0)
            val pulseSample = pulseEnv * 0.25 * sin(2.0 * PI * 80.0 * pulseT)

            // Master mix
            val mixed = (chordSample * 0.45 + bellSample * 0.40 + pulseSample * 0.15) * 0.85
            val clamped = mixed.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 32000.0).toInt().toShort()
        }

        writeWavFile(outputFile, pcmData, SAMPLE_RATE, NUM_CHANNELS)
    }

    /**
     * Synthesizes a 20-second Lo-Fi focus chord loop with electric piano tone & warm bass.
     */
    private fun generateLoFiFocusTrack(outputFile: File) {
        val durationSeconds = 20
        val totalSamples = SAMPLE_RATE * durationSeconds
        val pcmData = ShortArray(totalSamples)

        // Dm7 -> G7 -> Cmaj7 -> Am7
        val noteD3 = 146.83
        val noteF3 = 174.61
        val noteA3 = 220.00
        val noteC4 = 261.63

        val noteG2 = 98.00
        val noteB3 = 246.94
        val noteD4 = 293.66
        val noteF4 = 349.23

        val noteC3 = 130.81
        val noteE3 = 164.81
        val noteG3 = 196.00
        val noteB4 = 493.88

        val noteA2 = 110.00
        val noteE4 = 329.63

        val chords = listOf(
            listOf(noteD3, noteF3, noteA3, noteC4), // Dm7
            listOf(noteG2, noteB3, noteD4, noteF4), // G7
            listOf(noteC3, noteE3, noteG3, noteB3), // Cmaj7
            listOf(noteA2, noteC4, noteE4, noteA3)  // Am7
        )

        val chordDuration = totalSamples / 4

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val chordIdx = (i / chordDuration).coerceIn(0, 3)
            val activeChord = chords[chordIdx]
            val chordSampleTime = (i % chordDuration).toDouble() / SAMPLE_RATE

            // E-Piano decay
            val chordEnv = (0.7 * exp(-chordSampleTime * 1.2) + 0.3 * exp(-chordSampleTime * 0.2))

            var epiano = 0.0
            for ((idx, f) in activeChord.withIndex()) {
                val fDetuned = f * (1.0 + (idx - 1.5) * 0.001) // subtle chorus
                val tone = sin(2.0 * PI * fDetuned * t) +
                        0.3 * sin(4.0 * PI * fDetuned * t) +
                        0.1 * sin(6.0 * PI * fDetuned * t)
                epiano += tone / activeChord.size
            }

            // Warm Bassline
            val bassFreq = activeChord.first() * 0.5
            val bass = 0.4 * sin(2.0 * PI * bassFreq * t) + 0.2 * sin(4.0 * PI * bassFreq * t)

            // Vinyl soft warmth
            val vinyl = (sin(2.0 * PI * 60.0 * t) * 0.02)

            val mixed = (epiano * chordEnv * 0.55 + bass * 0.35 + vinyl) * 0.85
            val clamped = mixed.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 32000.0).toInt().toShort()
        }

        writeWavFile(outputFile, pcmData, SAMPLE_RATE, NUM_CHANNELS)
    }

    private fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * BITS_PER_SAMPLE / 8
        val blockAlign = channels * BITS_PER_SAMPLE / 8
        val subChunk2Size = pcmData.size * 2
        val chunkSize = 36 + subChunk2Size

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF chunk descriptor
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))

            // fmt sub-chunk
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat: PCM = 1
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(BITS_PER_SAMPLE.toShort())

            // data sub-chunk
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(subChunk2Size)

            fos.write(header.array())

            // Write PCM audio data
            val buffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                buffer.putShort(sample)
            }
            fos.write(buffer.array())
        }
    }
}
