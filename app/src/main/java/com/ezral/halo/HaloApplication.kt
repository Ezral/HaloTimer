package com.ezral.halo

import android.app.Application
import com.ezral.halo.runtime.TimerCoordinator

class HaloApplication : Application() {
    val coordinator by lazy { TimerCoordinator(this) }
}
