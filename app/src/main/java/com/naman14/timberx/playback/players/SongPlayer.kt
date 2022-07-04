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

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.naman14.timberx.R
import com.naman14.timberx.constants.Constants.ACTION_REPEAT_QUEUE
import com.naman14.timberx.constants.Constants.ACTION_REPEAT_SONG
import com.naman14.timberx.constants.Constants.REPEAT_MODE
import com.naman14.timberx.constants.Constants.SHUFFLE_MODE
import com.naman14.timberx.db.QueueDao
import com.naman14.timberx.db.QueueEntity
import com.naman14.timberx.extensions.asString
import com.naman14.timberx.extensions.isPlaying
import com.naman14.timberx.extensions.position
import com.naman14.timberx.extensions.toSongIDs
import com.naman14.timberx.models.Song
import com.naman14.timberx.repository.SongsRepository
import com.naman14.timberx.util.MusicUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.util.concurrent.TimeUnit

typealias OnIsPlaying = SongPlayer.(playing: Boolean) -> Unit

/**
 * A wrapper around [MusicPlayer] that specifically manages playing [Song]s and
 * links up with [Queue].
 *
 * @author Aidan Follestad (@afollestad)
 */
interface SongPlayer {

    fun setQueue(
        data: LongArray = LongArray(0),
        title: String = ""
    )

    fun getSession(): MediaSessionCompat

    fun playSong()

    fun playSong(id: Long)

    fun playSong(song: Song)

    fun seekTo(position: Int)

    fun pause()

    fun nextSong()

    fun repeatSong()

    fun repeatQueue()

    fun previousSong()

    fun playNext(id: Long)

    fun swapQueueSongs(from: Int, to: Int)

    fun removeFromQueue(id: Long)

    fun stop()

    fun release()

    fun onPlayingState(playing: OnIsPlaying)

    fun onPrepared(prepared: OnPrepared<SongPlayer>)

    fun onError(error: OnError<SongPlayer>)

    fun onCompletion(completion: OnCompletion<SongPlayer>)

    fun updatePlaybackState(applier: PlaybackStateCompat.Builder.() -> Unit)

    fun setPlaybackState(state: PlaybackStateCompat)

    fun restoreFromQueueData(queueData: QueueEntity)

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

    fun sleep(var1: Boolean)
}

class RealSongPlayer(
    private val context: Application,
    private val musicPlayer: MusicPlayer,
    private val songsRepository: SongsRepository,
    private val queueDao: QueueDao,
    private val queue: Queue
) : SongPlayer, AudioManager.OnAudioFocusChangeListener {

    private var isInitialized: Boolean = false

    private var isPlayingCallback: OnIsPlaying = {}
    private var preparedCallback: OnPrepared<SongPlayer> = {}
    private var errorCallback: OnError<SongPlayer> = {}
    private var completionCallback: OnCompletion<SongPlayer> = {}

    private var metadataBuilder = MediaMetadataCompat.Builder()
    private var stateBuilder = createDefaultPlaybackState()

    private var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var mLossTransient = false

    private var mSleepTimerDisposable: Disposable? = null
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context)

    @SuppressLint("UnspecifiedImmutableFlag")
    private var mediaSession = MediaSessionCompat(context, context.getString(R.string.app_name)).apply {
        setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(MediaSessionCallback(this, this@RealSongPlayer, songsRepository, queueDao))
        setPlaybackState(stateBuilder.build())

        val sessionIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val sessionActivityPendingIntent = PendingIntent.getActivity(context, 0, sessionIntent, 0)
        setSessionActivity(sessionActivityPendingIntent)
        isActive = true
    }

    init {
        queue.setMediaSession(mediaSession)

        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context)
        sp.getString("set_eqparam","")?.toString()?.let { setEqparam(it) }
        sp.getInt("set_stereo_width_preference", 10).toFloat()
            .let { setStereoWidth(it) }
        setEnabledEffect(sp.getBoolean("set_enabled_effect_preference", false))
        setEnabledStereoWidth(sp.getBoolean("set_enabled_stereo_width_preference", false))
        sp.getString("set_samplerate_preference", "44100")?.toInt()
            ?.let { setSampleRate(it) }
        setEnabledChafen(sp.getBoolean("set_enabled_chafen_preference", false))
        setChafenDelay(sp.getInt("set_chafen_preference", 20))

        musicPlayer.onPrepared {
            preparedCallback(this@RealSongPlayer)
            playSong()
            seekTo(getSession().position().toInt())
        }

        musicPlayer.onCompletion {
            completionCallback(this@RealSongPlayer)
            val controller = getSession().controller
            when (controller.repeatMode) {
                REPEAT_MODE_ONE -> {
                    controller.transportControls.sendCustomAction(ACTION_REPEAT_SONG, null)
                }
                REPEAT_MODE_ALL -> {
                    controller.transportControls.sendCustomAction(ACTION_REPEAT_QUEUE, null)
                }
                else -> controller.transportControls.skipToNext()
            }
        }

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(this@RealSongPlayer, Handler(Looper.getMainLooper()))
                build()
            }
        }

    }

    override fun setQueue(
        data: LongArray,
        title: String
    ) {
        Timber.d("""setQueue: ${data.asString()} ("$title"))""")
        this.queue.ids = data
        this.queue.title = title
    }

    override fun getSession(): MediaSessionCompat = mediaSession

    override fun playSong() {
        Timber.d("playSong()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        queue.ensureCurrentId()

        if (isInitialized) {
            updatePlaybackState {
                setState(STATE_PLAYING, mediaSession.position(), 1F)
            }
            musicPlayer.play()
            return
        }
        musicPlayer.reset()

        val path = MusicUtils.getSongUri(queue.currentSongId).toString()
        val isSourceSet = if (path.startsWith("content://")) {
            musicPlayer.setSource(path.toUri())
        } else {
            musicPlayer.setSource(path)
        }
        if (isSourceSet) {
            isInitialized = true
            musicPlayer.prepare()
        }
    }

    override fun playSong(id: Long) {
        Timber.d("playSong(): $id")
        val song = songsRepository.getSongForId(id)
        playSong(song)
    }

    override fun playSong(song: Song) {
        Timber.d("playSong(): ${song.title}")
        if (queue.currentSongId != song.id) {
            queue.currentSongId = song.id
            isInitialized = false
            updatePlaybackState {
                setState(STATE_STOPPED, 0, 1F)
            }
        }
        setMetaData(song)
        playSong()
    }

    override fun seekTo(position: Int) {
        Timber.d("seekTo(): $position")
        var pos: Int
        pos = position
        if (position==0)
            pos=1
        if (isInitialized) {
            musicPlayer.seekTo(pos)
            updatePlaybackState {
                setState(
                    mediaSession.controller.playbackState.state,
                    pos.toLong(),
                    1F
                )
            }
        }
    }

    override fun pause() {
        Timber.d("pause()")
        if (musicPlayer.isPlaying() && isInitialized) {
            musicPlayer.pause()
            updatePlaybackState {
                setState(STATE_PAUSED, mediaSession.position(), 1F)
            }
        }
    }

    override fun nextSong() {
        Timber.d("nextSong()")
        queue.nextSongId?.let {
            playSong(it)
        } ?: pause()
    }

    override fun repeatSong() {
        Timber.d("repeatSong()")
        updatePlaybackState {
            setState(STATE_STOPPED, 0, 1F)
        }
        musicPlayer.prepare()
        playSong(queue.currentSong())
    }

    override fun repeatQueue() {
        Timber.d("repeatQueue()")
        if (queue.currentSongId == queue.lastId())
            playSong(queue.firstId())
        else {
            nextSong()
        }
    }

    override fun previousSong() {
        Timber.d("previousSong()")
        queue.previousSongId?.let(::playSong)
    }

    override fun playNext(id: Long) {
        Timber.d("playNext(): $id")
        queue.moveToNext(id)
    }

    override fun swapQueueSongs(from: Int, to: Int) {
        Timber.d("swapQueueSongs(): $from -> $to")
        queue.swap(from, to)
    }

    override fun removeFromQueue(id: Long) {
        Timber.d("removeFromQueue(): $id")
        queue.remove(id)
    }

    override fun stop() {
        Timber.d("stop()")
        musicPlayer.stop()
        updatePlaybackState {
            setState(STATE_NONE, 0, 1F)
        }
    }

    override fun release() {
        Timber.d("release()")
        mediaSession.apply {
            isActive = false
            release()
        }
        musicPlayer.release()
        queue.reset()
    }

    override fun onPlayingState(playing: OnIsPlaying) {
        this.isPlayingCallback = playing
    }

    override fun onPrepared(prepared: OnPrepared<SongPlayer>) {
        this.preparedCallback = prepared
    }

    override fun onError(error: OnError<SongPlayer>) {
        this.errorCallback = error
        musicPlayer.onError { throwable ->
            errorCallback(this@RealSongPlayer, throwable)
        }
    }

    override fun onCompletion(completion: OnCompletion<SongPlayer>) {
        this.completionCallback = completion
    }

    override fun updatePlaybackState(applier: PlaybackStateCompat.Builder.() -> Unit) {
        applier(stateBuilder)
        setPlaybackState(stateBuilder.build())
    }

    override fun setPlaybackState(state: PlaybackStateCompat) {
        mediaSession.setPlaybackState(state)
        state.extras?.let { bundle ->
            mediaSession.setRepeatMode(bundle.getInt(REPEAT_MODE))
            mediaSession.setShuffleMode(bundle.getInt(SHUFFLE_MODE))
        }
        if (state.isPlaying) {
            isPlayingCallback(this, true)
        } else {
            isPlayingCallback(this, false)
        }
    }

    override fun restoreFromQueueData(queueData: QueueEntity) {
        queue.currentSongId = queueData.currentId ?: -1
        val playbackState = queueData.playState ?: STATE_NONE
        val currentPos = queueData.currentSeekPos ?: 0
        val repeatMode = queueData.repeatMode ?: REPEAT_MODE_NONE
        val shuffleMode = queueData.shuffleMode ?: SHUFFLE_MODE_NONE

        val queueIds = queueDao.getQueueSongsSync().toSongIDs()
        setQueue(queueIds, queueData.queueTitle)
        setMetaData(queue.currentSong())

        val extras = Bundle().apply {
            putInt(REPEAT_MODE, repeatMode)
            putInt(SHUFFLE_MODE, shuffleMode)
        }
        updatePlaybackState {
            setState(playbackState, currentPos, 1F)
            setExtras(extras)
        }
    }

    override fun setSampleRate(sampleRate: Int) {
        musicPlayer.setSampleRate(sampleRate)
    }

    override fun setEnabledEffect(enabledEffect: Boolean) {
        musicPlayer.setEnabledEffect(enabledEffect)
    }

    override fun setEnabledStereoWidth(enabledStereoWidth: Boolean) {
        musicPlayer.setEnabledStereoWidth(enabledStereoWidth)
    }

    override fun setStereoWidth(stereoWidth: Float) {
        musicPlayer.setStereoWidth(stereoWidth)
    }

    override fun setEnabledChafen(enabledChafen: Boolean) {
        musicPlayer.setEnabledChafen(enabledChafen)
    }

    override fun setChafenDelay(chafenDelay: Int) {
        musicPlayer.setChafenDelay(chafenDelay)
    }

    override fun setEqparam(eqparam: String) {
        musicPlayer.setEqparam(eqparam)
    }

    override fun setEnabledCompressor(var1: Boolean) {
        musicPlayer.setEnabledCompressor(var1)
    }

    override fun setThreshold(var1: Float) {
        musicPlayer.setThreshold(var1)
    }

    override fun setRatio(var1: Double) {
        musicPlayer.setRatio(var1)
    }

    override fun setAttack(var1: Double) {
        musicPlayer.setAttack(var1)
    }

    override fun setReleaseTime(var1: Double) {
        musicPlayer.setReleaseTime(var1)
    }

    override fun setGain(var1: Double) {
        musicPlayer.setGain(var1)
    }

    override fun setAutoGain(var1: Boolean) {
        musicPlayer.setAutoGain(var1)
    }

    override fun setDetectionType(var1: String?) {
        musicPlayer.setDetectionType(var1)
    }

    override fun setThresholdWidth(var1: Int) {
        musicPlayer.setThresholdWidth(var1)
    }

    private fun setMetaData(song: Song) {
        // TODO make music utils injectable
        val artwork = MusicUtils.getAlbumArtBitmap(context, song.albumId)
        val mediaMetadata = metadataBuilder.apply {
            putString(METADATA_KEY_ALBUM, song.album)
            putString(METADATA_KEY_ARTIST, song.artist)
            putString(METADATA_KEY_TITLE, song.title)
            putString(METADATA_KEY_ALBUM_ART_URI, song.albumId.toString())
            putBitmap(METADATA_KEY_ALBUM_ART, artwork)
            putString(METADATA_KEY_MEDIA_ID, song.id.toString())
            putLong(METADATA_KEY_DURATION, song.duration.toLong())
        }.build()
        mediaSession.setMetadata(mediaMetadata)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                mLossTransient=false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                var playing:Boolean = musicPlayer.isPlaying()
                pause()
                mLossTransient = playing
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if(mLossTransient){
                    playSong()
                }
               mLossTransient=false
            }
        }
    }

    override fun sleep(var1: Boolean){
        val time: Long = sp.getInt("set_sleep_time_preference",30).toLong()
        when(var1){
            true -> {
                mSleepTimerDisposable = Observable.timer(time, TimeUnit.MINUTES)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { doAction() }
            }
            false -> {
                disposeLastSleepTimer()
            }
        }
    }

    private fun doAction() {
        stop()
        val editor = sp.edit()
        editor.putBoolean("set_sleep_preference",false)
        editor.apply()
        Timber.d("sleepTimeOut")
    }

    private fun disposeLastSleepTimer() {
        if (mSleepTimerDisposable == null || mSleepTimerDisposable!!.isDisposed) {
            return
        }
        mSleepTimerDisposable!!.dispose()
        Timber.d("disposeLastSleepTimer")
    }

}

private fun createDefaultPlaybackState(): PlaybackStateCompat.Builder {
    return PlaybackStateCompat.Builder().setActions(
            ACTION_PLAY
                    or ACTION_PAUSE
                    or ACTION_PLAY_FROM_SEARCH
                    or ACTION_PLAY_FROM_MEDIA_ID
                    or ACTION_PLAY_PAUSE
                    or ACTION_SKIP_TO_NEXT
                    or ACTION_SKIP_TO_PREVIOUS
                    or ACTION_SET_SHUFFLE_MODE
                    or ACTION_SET_REPEAT_MODE)
            .setState(STATE_NONE, 0, 1f)
}
