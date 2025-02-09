package com.flxrs.dankchat.main.stream

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.trackPipAnimationHintView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flxrs.dankchat.databinding.FragmentStreamWebViewWrapperBinding
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.main.StreamWebViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
This fragment's purpose is to manage the lifecycle of the WebView inside it
it removes the StreamWebView before the fragment is destroyed to prevent the WebView from being destroyed along with it.
 */
class StreamWebViewWrapperFragment : Fragment() {
    private val mainViewModel: MainViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val streamWebViewModel: StreamWebViewModel by viewModel(ownerProducer = { requireParentFragment() })
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    activity?.trackPipAnimationHintView(streamWebView)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.streamWrapper.removeAllViews()
        bindingRef = null
        super.onDestroyView()
    }
}
