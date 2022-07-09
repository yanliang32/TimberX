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
package com.naman14.timberx.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.savedinstancestate.savedInstanceState
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.UriUtils
import com.google.gson.reflect.TypeToken
import com.naman14.timberx.R
import com.naman14.timberx.constants.Constants.ARTIST
import com.naman14.timberx.constants.Constants.SONG
import com.naman14.timberx.databinding.FragmentLyricsBinding
import com.naman14.timberx.extensions.*
import com.naman14.timberx.network.Outcome
import com.naman14.timberx.network.api.LyricsRestService
import com.naman14.timberx.ui.fragments.base.BaseNowPlayingFragment
import com.naman14.timberx.util.AutoClearedValue
import com.naman14.timberx.util.MusicUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

data class LyricContent(
    var sgc: Boolean,
    var sfy: Boolean,
    var qfy: Boolean,
    var lrc: Lrc
)

data class Lrc(
    var version: Int,
    var lyric: String
)

data class SongContent(
    var result: SongResult
)

data class SongResult(
    var songs: List<SongDetal>
)

data class SongDetal(
    var id: String,
    var name: String
)

class LyricsFragment : BaseNowPlayingFragment() {
    companion object {
        fun newInstance(artist: String, title: String): LyricsFragment {
            return LyricsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARTIST, artist)
                    putString(SONG, title)
                }
            }
        }

        private var lyricPosition:Long = 0
        private var lyricLastUpdateTime:Long = 0
        private var currentSongId:String = ""
    }

    private lateinit var artistName: String
    lateinit var songTitle: String
    var binding by AutoClearedValue<FragmentLyricsBinding>(this)

    private val lyricsService by inject<LyricsRestService>()
    private var mLyricsDisposable: Disposable? = null



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = inflater.inflateWithBinding(R.layout.fragment_lyrics, container)

        artistName = argument(ARTIST)
        songTitle = argument(SONG)
        return binding.root
    }

    override fun onStop() {
        super.onStop()
        disposeLastSleepTimer()
    }

    override fun onPause() {
        super.onPause()
        disposeLastSleepTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeLastSleepTimer()
    }

    private fun doAction(duration: Int) {
        if (lyricPosition<=duration)
        {
            binding.customLyricView.setCurrentTimeMillis((SystemClock.elapsedRealtime()-lyricLastUpdateTime)+lyricPosition)
            Timber.d("$lyricPosition-$lyricLastUpdateTime")
            //lyricPosition=lyricPosition+1000
        }
        else{
            disposeLastSleepTimer()
        }
    }

    private fun disposeLastSleepTimer() {
        if (mLyricsDisposable == null || mLyricsDisposable!!.isDisposed) {
            return
        }
        mLyricsDisposable!!.dispose()
        Timber.d("disposeLastSleepTimer")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.songTitle = songTitle

        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context)

        binding.customLyricView.setOnPlayerClickListener { progress, content ->
            run {
                mainViewModel.transportControls().seekTo(progress)
                Timber.d(progress.toString())
            }
        }

        binding.let {
            it.lifecycleOwner = this
            nowPlayingViewModel.currentData.observe(this) {
                val pos = nowPlayingViewModel.currentData.value?.position?.toLong()!!
                Timber.d("比对时间节点："+lyricPosition+"-"+pos)
                if(lyricPosition!=pos)
                {
                    lyricPosition =pos
                    lyricLastUpdateTime = SystemClock.elapsedRealtime()
                    Timber.d("更新position："+lyricPosition+"-"+lyricLastUpdateTime)
                }

                val duration: Int? = nowPlayingViewModel.currentData.value?.duration
                val songId = nowPlayingViewModel.currentData.value?.mediaId
                val songUri = songId?.let { it1 -> MusicUtils.getSongUri(it1.toLong()) }
                val songPath = UriUtils.uri2File(songUri).toString()
                val LyricPath = songPath.replace("[.](.*)".toRegex(),".lrc")
                songTitle = nowPlayingViewModel.currentData.value?.title.toString()
                artistName = nowPlayingViewModel.currentData.value?.artist.toString()
                Timber.d(LyricPath)
                if(currentSongId!=songId){
                    binding.customLyricView.reset()
                    if (songId != null) {
                        currentSongId = songId
                    }
                }

                if(FileUtils.isFileExists(LyricPath)){
                    binding.downLyricProgress.isGone=true

                    binding.customLyricView.setLyricFile(File(LyricPath))
                    //binding.customLyricView.setCurrentTimeMillis((SystemClock.elapsedRealtime()-lyricLastUpdateTime)+lyricPosition)
                    if(nowPlayingViewModel.currentData.value?.state==3)
                    {

                        disposeLastSleepTimer()
                        mLyricsDisposable = Observable.interval(1, TimeUnit.SECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                if (duration != null) {
                                    doAction(duration)
                                }
                            }

                    }
                    else{
                        disposeLastSleepTimer()
                    }
                }
                else if(sp.getBoolean("lyric_download",false))
                {
                    binding.downLyricProgress.isGone=false
                    if (duration != null) {
                        downloadLyric(LyricPath,duration)
                    }

                }
            }
        }




    }

    private fun downloadLyric(lyricPath:String,duration: Int){
        var songId: String
        lyricsService.getSongId(songTitle+" "+artistName)
            .ioToMain()
            .subscribeForOutcome { outcome ->
                when (outcome) {
                    is Outcome.Success -> {
                        songId = GsonUtils.fromJson<SongContent>(
                            outcome.data,
                            object : TypeToken<SongContent>() {}.type
                        ).result.songs[0].id

                        lyricsService.getLyrics(songId)
                            .ioToMain()
                            .subscribeForOutcome { outcome ->
                                when (outcome) {
                                    is Outcome.Success -> {
                                        binding.downLyricProgress.isGone=true
                                        val lyr:String =GsonUtils.fromJson<LyricContent>(outcome.data,
                                            object : TypeToken<LyricContent>() {}.type
                                        ).lrc.lyric
                                            //.replace("\\[.*\\]".toRegex(),"")
                                        FileIOUtils.writeFileFromString(lyricPath,lyr)
                                        binding.customLyricView.setLyricFile(File(lyricPath))

                                        //binding.customLyricView.setCurrentTimeMillis((SystemClock.elapsedRealtime()-lyricLastUpdateTime)+lyricPosition)
                                        if(nowPlayingViewModel.currentData.value?.state==3)
                                        {
                                            disposeLastSleepTimer()
                                            mLyricsDisposable = Observable.interval(1, TimeUnit.SECONDS)
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe {
                                                    doAction(duration)
                                                }
                                        }
                                        else{
                                            disposeLastSleepTimer()
                                        }
                                    }
                                }
                            }
                            .disposeOnDetach(view)
                    }
                }
            }
            .disposeOnDetach(view)
    }
}
