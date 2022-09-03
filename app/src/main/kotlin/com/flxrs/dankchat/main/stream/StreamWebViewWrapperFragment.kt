package com.flxrs.dankchat.main.stream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.databinding.FragmentStreamWebViewWrapperBinding
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.main.StreamWebViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import dagger.hilt.android.AndroidEntryPoint

/**
This fragment's purpose is to manage the lifecycle of the WebView inside it
it removes the StreamWebView before the fragment is destroyed to prevent the WebView from being destroyed along with it.
 */
@AndroidEntryPoint
class StreamWebViewWrapperFragment : Fragment() {
    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })
    private val streamWebViewModel: StreamWebViewModel by viewModels({ requireParentFragment() })
    private var bindingRef: FragmentStreamWebViewWrapperBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentStreamWebViewWrapperBinding.inflate(inflater, container, false).also {
        bindingRef = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val streamWebView = streamWebViewModel.getOrCreateStreamWebView()
        binding.streamWrapper.addView(
            streamWebView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        collectFlow(mainViewModel.currentStreamedChannel) {
            streamWebViewModel.setStream(it, streamWebView)
        }
    }

    override fun onDestroyView() {
        binding.streamWrapper.removeAllViews()
        bindingRef = null
        super.onDestroyView()
    }
}