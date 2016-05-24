package org.sugr.gearshift

import android.util.Log

object C {
    val PREF_PROFILES = "profiles"
    val PROFILES_PREF_NAME = "profiles"
}

private object c {
    val TAG = "GearShift"
}

fun logD(message: String) = Log.d(c.TAG, message)
fun logV(message: String) = Log.v(c.TAG, message)
fun logI(message: String) = Log.i(c.TAG, message)
fun logW(message: String) = Log.w(c.TAG, message)
fun logE(message: String, e: Throwable) = Log.e(c.TAG, message, e)