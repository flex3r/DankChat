package com.flxrs.dankchat.main.stream

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.flxrs.dankchat.R
import com.flxrs.dankchat.main.MainAndroidViewModel
import com.flxrs.dankchat.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StreamWebViewWrapperFragment : Fragment() {
    private lateinit var insertion: FrameLayout
    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })
    private val mainAndroidViewModel: MainAndroidViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_stream_web_view_wrapper, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        insertion = view.findViewById(R.id.stream_wrapper)
    }

    override fun onStart() {
        Log.d("DANK", "add view")
        insertion.addView(
            mainAndroidViewModel.streamWebView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        Log.d("DANK", "launching")
        lifecycleScope.launch {
            mainViewModel.currentStreamedChannel.collect {
                Log.d("DANK", "channel $it")
                mainAndroidViewModel.streamWebView.setStream(it)
            }
        }
        Log.d("DANK", "launched")
        super.onStart()
    }

    override fun onStop() {
        Log.d("DANK", "remove view")
        insertion.removeView(mainAndroidViewModel.streamWebView)
        super.onStop()
    }



}