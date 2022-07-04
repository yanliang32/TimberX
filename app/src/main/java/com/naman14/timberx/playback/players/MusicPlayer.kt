/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.naman14.timberx.playback.players

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import com.blankj.utilcode.util.UriUtils
import com.zj.jplayercore.controller.JEqparam
import com.zj.jplayercore.controller.JPlayer
import timber.log.Timber


typealias OnPrepared<T> = T.() -> Unit
typealias OnError<T> = T.(error: Throwable) -> Unit
typealias OnCompletion<T> = T.() -> Unit

/**
 * An injectable wrapper around [MediaPlayer].
 *
 * @author Aidan Follestad (@afollestad)
 */
interface MusicPlayer {
    fun play()

    fun setSource(path: String): Boolean

    fun setSource(uri: Uri): Boolean

    fun prepare()

    fun seekTo(position: Int)

    fun isPrepared(): Boolean

    fun isPlaying(): Boolean

    fun position(): Int

    fun pause()

    fun stop()

    fun reset()

    fun release()

    fun onPrepared(prepared: OnPrepared<MusicPlayer>)

    fun onError(error: OnError<MusicPlayer>)

    fun onCompletion(completion: OnCompletion<MusicPlayer>)

    fun setSampleRate(sampleRate: Int)

    fun setEnabledEffect(enabledEffect: Boolean)

    fun setEnabledStereoWidth(enabledStereoWidth: Boolean)

    fun setStereoWidth(stereoWidth: Float)

    fun setEnabledChafen(enabledChafen: Boolean)

    fun setChafenDelay(chafenDelay: Int)

    fun setEqparam(eqparam: String)

    /**
     * 设置播放器是否开启硬膝压缩器。
     */
    fun setEnabledCompressor(var1: Boolean)

    /**
     * 设置播放器硬膝压缩器阈值。
     */
    fun setThreshold(var1: Float)

    /**
     * 设置播放器硬膝压缩器压缩比。
     */
    fun setRatio(var1: Double)

    /**
     * 设置播放器硬膝压缩器启动时间。
     */
    fun setAttack(var1: Double)

    /**
     * 设置播放器硬膝压缩器释放时间。
     */
    fun setReleaseTime(var1: Double)

    /**
     * 设置播放器硬膝压缩器增益。
     */
    fun setGain(var1: Double)

    /**
     * 设置播放器硬膝压缩器是否开启自动增益。
     */
    fun setAutoGain(var1: Boolean)

    /**
     * 设置播放器。
     */
    fun setDetectionType(var1: String?)

    /**
     * 设置播放器硬膝压缩器阈值宽度。
     */
    fun setThresholdWidth(var1: Int)
}

class RealMusicPlayer(internal val context: Application) : MusicPlayer,
    JPlayer.OnPreparedListener,
    JPlayer.OnErrorListener,
    JPlayer.OnCompletionListener {

    private var _player: JPlayer? = null
    private val player: JPlayer
        get() {
            if (_player == null) {
                _player = createPlayer(this)
            }
            return _player ?: throw IllegalStateException("Impossible")
        }

    private var didPrepare = false
    private var onPrepared: OnPrepared<MusicPlayer> = {}
    private var onError: OnError<MusicPlayer> = {}
    private var onCompletion: OnCompletion<MusicPlayer> = {}
    private val jEqparams = arrayListOf<JEqparam>()

    override fun play() {
        Timber.d("play()")
        player.start()
    }

    override fun setSource(path: String): Boolean {
        Timber.d("setSource() - $path")
        try {
            player.setDataSource(path)
        } catch (e: Exception) {
            Timber.d("setSource() - failed")
            onError(this, e)
            return false
        }
        return true
    }

    override fun setSource(uri: Uri): Boolean {
        Timber.d("setSource() - $uri")
        try {
            val file = UriUtils.uri2File(uri)
            if (null == file || !file.exists()) {
                RuntimeException("读取文件有误")
            }
            player.setDataSource(file!!.absolutePath)
        } catch (e: Exception) {
            Timber.d("setSource() - failed")
            onError(this, e)
            return false
        }
        return true
    }

    override fun prepare() {
        Timber.d("prepare()")
        player.prepare()
    }

    override fun seekTo(position: Int) {
        Timber.d("seekTo($position)")
        player.seekTo(position)
    }

    override fun isPrepared() = didPrepare

    override fun isPlaying() = player.isPlaying

    override fun position() = player.currentPosition

    override fun pause() {
        Timber.d("pause()")
        player.pause()
    }

    override fun stop() {
        Timber.d("stop()")
        player.stop()
    }

    override fun reset() {
        Timber.d("reset()")
        player.reset()
    }

    override fun release() {
        Timber.d("release()")
        player.release()
        _player = null
    }

    override fun onPrepared(prepared: OnPrepared<MusicPlayer>) {
        this.onPrepared = prepared
    }

    override fun onError(error: OnError<MusicPlayer>) {
        this.onError = error
    }

    override fun onCompletion(completion: OnCompletion<MusicPlayer>) {
        this.onCompletion = completion
    }

    // Callbacks from stock MediaPlayer...

    override fun onPrepared(mp: JPlayer?) {
        Timber.d("onPrepared()")
        didPrepare = true
        onPrepared(this)
    }

    override fun onError(mp: JPlayer?, what: Int, extra: Int): Boolean {
        didPrepare = false
        Timber.d("onError() - what = $what, extra = $extra")
        return false
    }

    override fun onCompletion(mp: JPlayer?) {
        Timber.d("onCompletion()")
        onCompletion(this)
    }

    override fun setSampleRate(sampleRate: Int) {
        JPlayer.jConfig.setSampleRate(sampleRate)
        Timber.d("setSampleRate: $sampleRate")
    }

    override fun setEnabledEffect(enabledEffect: Boolean) {
        JPlayer.jConfig.isEnabledEffect = enabledEffect

        Timber.d("setEnabledEffect: $enabledEffect")
    }

    override fun setEnabledStereoWidth(enabledStereoWidth: Boolean) {
        JPlayer.jConfig.isEnabledStereoWidth = enabledStereoWidth

        Timber.d("enabledStereoWidth: $enabledStereoWidth")
    }

    override fun setStereoWidth(stereoWidth: Float) {
        JPlayer.jConfig.setStereoWidth(stereoWidth)

        Timber.d("setStereoWidth: $stereoWidth")
    }

    override fun setEnabledChafen(enabledChafen: Boolean) {
        JPlayer.jConfig.isEnabledChafen = enabledChafen
        Timber.d("setEnabledChafen: $enabledChafen")
    }

    override fun setChafenDelay(chafenDelay: Int) {
        JPlayer.jConfig.setChafenDelay(chafenDelay)
        Timber.d("setChafenDelay: $chafenDelay")
    }

    override fun setEqparam(eqparam: String) {
        jEqparams.clear()
        val eqList : List<String>
        if(eqparam.contains("\r\n")) {
            eqList = eqparam.split("\r\n")
        }
        else
        {
            eqList = eqparam.split("\n")
        }
        var preCut = "0"
        val precut = eqList[eqList.size - 1].split(" ").toTypedArray()
        if (precut[0] == "precut") {
            preCut = precut[1]
        }
        for (a in eqList) {
            val filter = a.split(" ").toTypedArray()
            if (filter.size == 5 && filter[0] == "filter") {
                val eq1 = JEqparam()
                eq1.setFreq(filter[2].toDouble())
                eq1.setPeak(filter[4].toDouble())
                eq1.setQ(filter[3].toDouble())
                eq1.setPrecut(preCut.toDouble())
                jEqparams.add(eq1)

            }
        }
        if (jEqparams.size>0) {
            player.loadEQ(jEqparams)
        }
        Timber.d("setEqparam: $eqparam")
    }

    override fun setEnabledCompressor(var1: Boolean) {
        JPlayer.jConfig.isEnabledCompressor = var1

        Timber.d("setEnabledCompressor: $var1")
    }

    override fun setThreshold(var1: Float) {
        JPlayer.jConfig.setThreshold(var1)
        Timber.d("setThreshold: $var1")
    }

    override fun setRatio(var1: Double) {
        JPlayer.jConfig.setRatio(var1)
        Timber.d("setRatio: $var1")
    }

    override fun setAttack(var1: Double) {
        JPlayer.jConfig.setAttack(var1)
        Timber.d("setAttack: $var1")
    }

    override fun setReleaseTime(var1: Double) {
        JPlayer.jConfig.setRelease(var1)
        Timber.d("setReleaseTime: $var1")
    }

    override fun setGain(var1: Double) {
        JPlayer.jConfig.setGain(var1)
        Timber.d("setGain: $var1")
    }

    override fun setAutoGain(var1: Boolean) {
        JPlayer.jConfig.isAutoGain = var1
        Timber.d("setAutoGain: $var1")
    }

    override fun setDetectionType(var1: String?) {
        JPlayer.jConfig.setDetectionType(var1)
        Timber.d("setDetectionType: $var1")
    }

    override fun setThresholdWidth(var1: Int) {
        JPlayer.jConfig.setThresholdWidth(var1)
        Timber.d("setThresholdWidth: $var1")
    }
}

private fun createPlayer(owner: RealMusicPlayer): JPlayer {

    return JPlayer().apply {
        setWakeMode(owner.context, PowerManager.PARTIAL_WAKE_LOCK)
//        val attr = AudioAttributes.Builder().apply {
//            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//            setUsage(AudioAttributes.USAGE_MEDIA)
//        }.build()
        //createAudioTrack(attr)
        setOnPreparedListener(owner)
        setOnCompletionListener(owner)
        setOnErrorListener(owner)
    }


}
