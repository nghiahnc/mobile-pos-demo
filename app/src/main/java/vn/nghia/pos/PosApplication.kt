package vn.nghia.pos

import android.app.Application
import vn.nghia.pos.di.AppComponent
import vn.nghia.pos.di.DaggerAppComponent

class PosApplication : Application() {
    val component: AppComponent by lazy { DaggerAppComponent.factory().create(applicationContext) }
}
