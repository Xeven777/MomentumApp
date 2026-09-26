package com.anish.momentum.utils

import android.app.Application
import com.anish.momentum.data.HabitRepository
import com.anish.momentum.data.MomentumDatabase
import com.anish.momentum.data.SettingsStore

/**
 * Tiny manual dependency container. The app has one database and two
 * repositories, so a DI framework would be dead weight.
 */
object ServiceLocator {
    private lateinit var app: Application

    val database: MomentumDatabase by lazy { MomentumDatabase.get(app) }
    val habits: HabitRepository by lazy { HabitRepository(database.habitDao()) }
    val settings: SettingsStore by lazy { SettingsStore(app) }

    fun init(application: Application) {
        app = application
    }
}

class Momentum : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
