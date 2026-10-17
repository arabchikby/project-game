package com.example.spacetycoon

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sin

/** Звуки без файлов: короткие синус-сигналы генерируются при старте и играются через SoundPool */
object Sfx {
    const val TAP = 0; const val CRIT = 1; const val BUY = 2; const val METEOR = 3; const val PRESTIGE = 4
    private var pool: SoundPool? = null
    private val ids = IntArray(5)

    fun init(ctx: Context) {
        if (pool != null) return
        // Удаляем остатки временных wav от старых запусков, если они остались после сбоя.
        ctx.cacheDir.listFiles()?.filter { it.isFile && it.name.matches(Regex("sfx\\d+\\.wav")) }
            ?.forEach { runCatching { it.delete() } }

        val sp = SoundPool.Builder().setMaxStreams(6)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        val tempFiles = mutableMapOf<Int, File>()
        sp.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (status == 0) {
                // SoundPool уже загрузил звук в память — файл больше не нужен.
                tempFiles.remove(sampleId)?.let { runCatching { it.delete() } }
            }
        }
        // (частота в начале Гц, частота в конце Гц, длительность мс, громкость)
        val specs = listOf(
            floatArrayOf(880f, 1100f, 45f, 0.35f),   // тап: «пик»
            floatArrayOf(180f, 60f, 260f, 0.8f),     // крит: «бум»
            floatArrayOf(1320f, 1760f, 160f, 0.4f),  // покупка: «дзынь»
            floatArrayOf(300f, 1500f, 320f, 0.35f),  // метеорит: «вжух»
            floatArrayOf(440f, 880f, 700f, 0.5f))    // престиж
        specs.forEachIndexed { i, p ->
            val f = File(ctx.cacheDir, "sfx$i.wav")
            f.writeBytes(wav(p[0], p[1], p[2].toInt(), p[3]))
            ids[i] = sp.load(f.path, 1)
            tempFiles[ids[i]] = f
        }
        pool = sp
    }

    fun play(i: Int) { pool?.play(ids[i], 1f, 1f, 1, 0, 1f) }

    private fun wav(f0: Float, f1: Float, ms: Int, vol: Float): ByteArray {
        val rate = 22050
        val n = rate * ms / 1000
        val pcm = ByteArray(n * 2)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / n.toFloat()
            phase += 2 * Math.PI * (f0 + (f1 - f0) * t) / rate
            val env = (1f - t) * min(1f, i / 80f)   // короткая атака и затухание
            val v = (sin(phase) * env * vol * 32767).toInt()
            pcm[i * 2] = (v and 0xFF).toByte(); pcm[i * 2 + 1] = (v shr 8).toByte()
        }
        val bb = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        bb.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return bb.array()
    }
}
