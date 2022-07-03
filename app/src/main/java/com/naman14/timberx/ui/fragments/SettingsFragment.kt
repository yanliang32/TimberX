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
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.naman14.timberx.R
import com.naman14.timberx.constants.Constants
import com.naman14.timberx.constants.Constants.ACTION_SELECT_EQPARAM
import com.naman14.timberx.constants.Constants.ACTION_SET_CHAFEN
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_CHAFEN
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_EFFECT
import com.naman14.timberx.constants.Constants.ACTION_SET_ENABLED_STEREO_WIDTH
import com.naman14.timberx.constants.Constants.ACTION_SET_EQPARAM
import com.naman14.timberx.constants.Constants.ACTION_SET_SAMPLERATE
import com.naman14.timberx.constants.Constants.ACTION_SET_STEREO_WIDTH
import com.naman14.timberx.ui.viewmodels.MainViewModel
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import timber.log.Timber


class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    protected val mainViewModel by sharedViewModel<MainViewModel>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference("set_samplerate_preference")?.onPreferenceChangeListener = this
        findPreference("set_enabled_effect_preference")?.onPreferenceChangeListener = this
        findPreference("set_enabled_stereo_width_preference")?.onPreferenceChangeListener = this
        findPreference("set_stereo_width_preference")?.onPreferenceChangeListener = this
        findPreference("set_enabled_chafen_preference")?.onPreferenceChangeListener = this
        findPreference("set_chafen_preference")?.onPreferenceChangeListener = this
        findPreference("select_eq_preference")?.onPreferenceChangeListener = this
        findPreference("set_eqparam")?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        when(preference!!.key){
            "set_samplerate_preference" -> {
                mainViewModel.transportControls().sendCustomAction(ACTION_SET_SAMPLERATE,Bundle().apply { putString(
                    Constants.SAMPLERATE, newValue.toString()) })
                //mainViewModel.transportControls().sendCustomAction(Constants.ACTION_REPEAT_SONG, null)
                Timber.d(newValue.toString())
            }
            "set_enabled_effect_preference" -> {
                mainViewModel.transportControls().sendCustomAction(ACTION_SET_ENABLED_EFFECT,Bundle().apply { putBoolean(
                    Constants.ENABLED_EFFECT, newValue as Boolean
                ) })
                Timber.d(newValue.toString())
            }
            "set_enabled_stereo_width_preference" -> {
                mainViewModel.transportControls().sendCustomAction(
                    ACTION_SET_ENABLED_STEREO_WIDTH,Bundle().apply { putBoolean(
                        Constants.ENABLED_STEREO_WIDTH, newValue as Boolean) })
                Timber.d(newValue.toString())
            }
            "set_stereo_width_preference" -> {
                mainViewModel.transportControls().sendCustomAction(ACTION_SET_STEREO_WIDTH,Bundle().apply { putString(
                    Constants.STEREO_WIDTH, newValue.toString()) })
                Timber.d(newValue.toString())
            }
            "set_enabled_chafen_preference" -> {
                mainViewModel.transportControls().sendCustomAction(ACTION_SET_ENABLED_CHAFEN,Bundle().apply { putBoolean(
                    Constants.ENABLED_CHAFEN, newValue as Boolean) })
                Timber.d(newValue.toString())
            }
            "set_chafen_preference" -> {
                mainViewModel.transportControls().sendCustomAction(ACTION_SET_CHAFEN,Bundle().apply { putString(
                    Constants.CHAFEN, newValue.toString()) })
                Timber.d(newValue.toString())
            }
            "select_eq_preference" -> {
                if(newValue.toString() != ""){
                    mainViewModel.transportControls().sendCustomAction(ACTION_SELECT_EQPARAM,Bundle().apply { putString(
                        Constants.PRESETEQ, newValue.toString()) })
                }
                else{
                    mainViewModel.transportControls().sendCustomAction(ACTION_SET_EQPARAM,Bundle().apply { putString(
                        Constants.EQPARAM, preference.sharedPreferences.getString("set_eqparam", "")) })
                }
                Timber.d(newValue.toString())
            }
            "set_eqparam" -> {
                if(preference.sharedPreferences.getString("select_eq_preference", "") == ""){
                    mainViewModel.transportControls().sendCustomAction(ACTION_SET_EQPARAM,Bundle().apply { putString(
                        Constants.EQPARAM, newValue.toString()) })
                }
                Timber.d(newValue.toString())
            }
        }
        return true
    }
}
