package com.flxrs.dankchat.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flxrs.dankchat.main.stream.StreamWebView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainAndroidViewModel @Inject constructor(application: Application): AndroidViewModel(application) {

    val streamWebView: StreamWebView  = StreamWebView(application) // how do I pass attrs and stuffs? FeelsDankMan

}
