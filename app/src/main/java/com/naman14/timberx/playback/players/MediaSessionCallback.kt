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

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import com.naman14.timberx.constants.Constants
import com.naman14.timberx.constants.Constants.ACTION_PLAY_NEXT
import com.naman14.timberx.constants.Constants.ACTION_QUEUE_REORDER
import com.naman14.timberx.constants.Constants.ACTION_REPEAT_QUEUE
import com.naman14.timberx.constants.Constants.ACTION_REPEAT_SONG
import com.naman14.timberx.constants.Constants.ACTION_RESTORE_MEDIA_SESSION
import com.naman14.timberx.constants.Constants.ACTION_SELECT_EQPARAM
import com.naman14.timberx.constants.Constants.ACTION_SET_ATTACK
import com.naman14.timberx.constants.Constants.ACTION_SET_AUTO_GAIN
import com.naman14.timberx.constants.Constants.ACTION_SET_CHAFEN
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_CHAFEN
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_COMPRESSOR
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_EFFECT
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_STEREO_WIDTH
import com.naman14.timberx.constants.Constants.ACTION_SET_EQPARAM
import com.naman14.timberx.constants.Constants.ACTION_SET_GAIN
import com.naman14.timberx.constants.Constants.ACTION_SET_MEDIA_STATE
import com.naman14.timberx.constants.Constants.ACTION_SET_RATIO
import com.naman14.timberx.constants.Constants.ACTION_SET_RELEASE_TIME
import com.naman14.timberx.constants.Constants.ACTION_SET_SAMPLERATE
import com.naman14.timberx.constants.Constants.ACTION_SET_SLEEP
import com.naman14.timberx.constants.Constants.ACTION_SET_STEREO_WIDTH
import com.naman14.timberx.constants.Constants.ACTION_SET_THRESHOLD
import com.naman14.timberx.constants.Constants.ACTION_SET_THRESHOLD_WIDTH
import com.naman14.timberx.constants.Constants.ACTION_SONG_DELETED
import com.naman14.timberx.constants.Constants.ATTACK
import com.naman14.timberx.constants.Constants.AUTO_GAIN
import com.naman14.timberx.constants.Constants.CHAFEN
import com.naman14.timberx.constants.Constants.ENABLED_CHAFEN
import com.naman14.timberx.constants.Constants.ENABLED_COMPRESSOR
import com.naman14.timberx.constants.Constants.ENABLED_EFFECT
import com.naman14.timberx.constants.Constants.ENABLED_STEREO_WIDTH
import com.naman14.timberx.constants.Constants.EQPARAM
import com.naman14.timberx.constants.Constants.GAIN
import com.naman14.timberx.constants.Constants.PRESETEQ
import com.naman14.timberx.constants.Constants.QUEUE_FROM
import com.naman14.timberx.constants.Constants.QUEUE_TITLE
import com.naman14.timberx.constants.Constants.QUEUE_TO
import com.naman14.timberx.constants.Constants.RATIO
import com.naman14.timberx.constants.Constants.RELEASE_TIME
import com.naman14.timberx.constants.Constants.REPEAT_MODE
import com.naman14.timberx.constants.Constants.SAMPLERATE
import com.naman14.timberx.constants.Constants.SEEK_TO_POS
import com.naman14.timberx.constants.Constants.SHUFFLE_MODE
import com.naman14.timberx.constants.Constants.SLEEP
import com.naman14.timberx.constants.Constants.SONG
import com.naman14.timberx.constants.Constants.SONGS_LIST
import com.naman14.timberx.constants.Constants.STEREO_WIDTH
import com.naman14.timberx.constants.Constants.THRESHOLD
import com.naman14.timberx.constants.Constants.THRESHOLD_WIDTH
import com.naman14.timberx.db.QueueDao
import com.naman14.timberx.models.MediaID
import com.naman14.timberx.repository.SongsRepository

class MediaSessionCallback(
    private val mediaSession: MediaSessionCompat,
    private val songPlayer: SongPlayer,
    private val songsRepository: SongsRepository,
    private val queueDao: QueueDao
) : MediaSessionCompat.Callback() {

    override fun onPause() = songPlayer.pause()

    override fun onPlay() = songPlayer.playSong()

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        query?.let {
            val song = songsRepository.searchSongs(query, 1)
            if (song.isNotEmpty()) {
                songPlayer.playSong(song.first())
            }
        } ?: onPlay()
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        val songId = MediaID().fromString(mediaId).mediaId!!.toLong()
        songPlayer.playSong(songId)

        if (extras == null) return
        val queue = extras.getLongArray(SONGS_LIST)
        val seekTo = extras.getInt(SEEK_TO_POS)
        val queueTitle = extras.getString(QUEUE_TITLE) ?: ""

        if (queue != null) {
            songPlayer.setQueue(queue, queueTitle)
        }
        if (seekTo > 0) {
            songPlayer.seekTo(seekTo)
        }
    }

    override fun onSeekTo(pos: Long) = songPlayer.seekTo(pos.toInt())

    override fun onSkipToNext() = songPlayer.nextSong()

    override fun onSkipToPrevious() = songPlayer.previousSong()

    override fun onStop() = songPlayer.stop()

    override fun onSetRepeatMode(repeatMode: Int) {
        super.onSetRepeatMode(repeatMode)
        val bundle = mediaSession.controller.playbackState.extras ?: Bundle()
        songPlayer.setPlaybackState(
                PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                        .setExtras(bundle.apply {
                            putInt(REPEAT_MODE, repeatMode)
                        }
                        ).build()
        )
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        super.onSetShuffleMode(shuffleMode)
        val bundle = mediaSession.controller.playbackState.extras ?: Bundle()
        songPlayer.setPlaybackState(
                PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                        .setExtras(bundle.apply {
                            putInt(SHUFFLE_MODE, shuffleMode)
                        }).build()
        )
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            ACTION_SET_MEDIA_STATE -> setSavedMediaSessionState()
            ACTION_REPEAT_SONG -> songPlayer.repeatSong()
            ACTION_REPEAT_QUEUE -> songPlayer.repeatQueue()

            ACTION_PLAY_NEXT -> {
                val nextSongId = extras!!.getLong(SONG)
                songPlayer.playNext(nextSongId)
            }

            ACTION_QUEUE_REORDER -> {
                val from = extras!!.getInt(QUEUE_FROM)
                val to = extras.getInt(QUEUE_TO)
                songPlayer.swapQueueSongs(from, to)
            }

            ACTION_SONG_DELETED -> {
                val id = extras!!.getLong(SONG)
                songPlayer.removeFromQueue(id)
            }

            ACTION_RESTORE_MEDIA_SESSION -> restoreMediaSession()

            ACTION_SET_SAMPLERATE ->{
                extras!!.getString(SAMPLERATE)?.toInt()?.let { songPlayer.setSampleRate(it) }
            }

            ACTION_SET_ENABLED_EFFECT ->{
                songPlayer.setEnabledEffect(extras!!.getBoolean(ENABLED_EFFECT))
            }

            ACTION_SET_ENABLED_STEREO_WIDTH ->{
                songPlayer.setEnabledStereoWidth(extras!!.getBoolean(ENABLED_STEREO_WIDTH))
            }

            ACTION_SET_STEREO_WIDTH ->{
                songPlayer.setStereoWidth(extras!!.getInt(STEREO_WIDTH).toFloat())
            }

            ACTION_SET_ENABLED_CHAFEN ->{
                songPlayer.setEnabledChafen(extras!!.getBoolean(ENABLED_CHAFEN))
            }

            ACTION_SET_CHAFEN ->{
                songPlayer.setChafenDelay(extras!!.getInt(CHAFEN))
            }

            ACTION_SELECT_EQPARAM ->{
                extras!!.getString(PRESETEQ)?.toString()?.let { songPlayer.setEqparam(it) }
            }

            ACTION_SET_EQPARAM ->{
                extras!!.getString(EQPARAM)?.toString()?.let { songPlayer.setEqparam(it) }
            }

            ACTION_SET_ENABLED_COMPRESSOR ->{
                songPlayer.setEnabledCompressor(extras!!.getBoolean(ENABLED_COMPRESSOR))
            }
            ACTION_SET_THRESHOLD ->{
                songPlayer.setThreshold(extras!!.getInt(THRESHOLD).toFloat())
            }
            ACTION_SET_RATIO ->{
                songPlayer.setRatio(extras!!.getInt(RATIO).toDouble())
            }
            ACTION_SET_ATTACK ->{
                songPlayer.setAttack(extras!!.getInt(ATTACK).toDouble())
            }
            ACTION_SET_RELEASE_TIME ->{
                songPlayer.setReleaseTime(extras!!.getInt(RELEASE_TIME).toDouble())
            }
            ACTION_SET_AUTO_GAIN ->{
                songPlayer.setAutoGain(extras!!.getBoolean(AUTO_GAIN))
            }
            ACTION_SET_GAIN ->{
                songPlayer.setGain(extras!!.getInt(GAIN).toDouble())
            }
            ACTION_SET_THRESHOLD_WIDTH ->{
                songPlayer.setThresholdWidth(extras!!.getInt(THRESHOLD_WIDTH))
            }
            ACTION_SET_SLEEP -> {
                songPlayer.sleep(extras!!.getBoolean(SLEEP))
            }
        }
    }

    private fun setSavedMediaSessionState() {
        // Only set saved session from db if we know there is not any active media session
        val controller = mediaSession.controller ?: return
        if (controller.playbackState == null || controller.playbackState.state == STATE_NONE) {
            val queueData = queueDao.getQueueDataSync() ?: return
            songPlayer.restoreFromQueueData(queueData)
        } else {
            // Force update the playback state and metadata from the media session so that the
            // attached Observer in NowPlayingViewModel gets the current state.
            restoreMediaSession()
        }
    }

    private fun restoreMediaSession() {
        songPlayer.setPlaybackState(mediaSession.controller.playbackState)
        mediaSession.setMetadata(mediaSession.controller.metadata)
    }
}
