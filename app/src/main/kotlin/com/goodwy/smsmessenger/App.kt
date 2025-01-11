package com.goodwy.smsmessenger

import com.goodwy.commons.RightApp
import com.goodwy.commons.extensions.isRuStoreInstalled
import com.goodwy.commons.helpers.rustore.RuStoreModule

class App : RightApp() {

    override fun onCreate() {
        super.onCreate()
        if (isRuStoreInstalled()) RuStoreModule.install(this, "685530047") //TODO rustore
    }

    override val isAppLockFeatureAvailable = true
}
