package com.flxrs.dankchat.main

import android.app.Application
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import com.flxrs.dankchat.main.stream.StreamWebView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainAndroidViewModel @Inject constructor(application: Application): AndroidViewModel(application) {

    val streamWebView: StreamWebView  = StreamWebView(application)

    override fun onCleared() {
        super.onCleared()
        streamWebView.destroy()
    }

}
