package com.gregory.mynotes.platform

import android.content.Context

object AndroidContext {
    lateinit var applicationContext: Context
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
}