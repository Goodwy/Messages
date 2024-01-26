package com.goodwy.smsmessenger

import android.app.Application
import com.goodwy.commons.extensions.checkUseEnglish
import com.goodwy.commons.helpers.rustore.RuStoreModule

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        RuStoreModule.install(this, "685530047") //TODO rustore
    }
}
