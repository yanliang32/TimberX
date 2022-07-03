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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.blankj.utilcode.util.GsonUtils
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
import org.koin.android.ext.android.inject

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
    }

    private lateinit var artistName: String
    lateinit var songTitle: String
    var binding by AutoClearedValue<FragmentLyricsBinding>(this)

    private val lyricsService by inject<LyricsRestService>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = inflater.inflateWithBinding(R.layout.fragment_lyrics, container)
        artistName = argument(ARTIST)
        songTitle = argument(SONG)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.songTitle = songTitle
        var songId: String
        // TODO make the lyrics handler/repo injectable

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
                                    is Outcome.Success -> binding.lyrics = GsonUtils.fromJson<LyricContent>(outcome.data,
                                        object : TypeToken<LyricContent>() {}.type
                                    ).lrc.lyric.replace("\\[.*\\]".toRegex(),"")
                                }
                            }
                            .disposeOnDetach(view)
                    }
                }
            }
            .disposeOnDetach(view)


    }
}
