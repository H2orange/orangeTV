package top.orangetv

import android.app.Application
import top.orangetv.di.AppContainer

class OrangeTvApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
