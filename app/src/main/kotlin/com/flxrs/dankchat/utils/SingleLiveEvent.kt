package com.flxrs.dankchat.utils

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        observe(owner, observer::onChanged)
    }

    @MainThread
    fun observe(owner: LifecycleOwner, observer: (T) -> Unit) {
        super.observe(owner, PendingObserver(observer))
    }

    override fun setValue(value: T) {
        pending.set(true)
        super.setValue(value)
    }

    private inner class PendingObserver<T>(private val observer: (T) -> Unit) : Observer<T> {
        override fun onChanged(t: T) {
            if (pending.compareAndSet(true, false)) {
                observer(t)
            }
        }
    }
}
