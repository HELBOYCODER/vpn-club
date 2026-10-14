package com.helboy.vpnclub.shortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.helboy.vpnclub.MainActivity
import com.helboy.vpnclub.R

/** User-visible activity handles VPN consent for the home-screen shortcut. */
class HellboyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_TOGGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val action = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val view = RemoteViews(context.packageName, R.layout.widget_hellboy)
            view.setOnClickPendingIntent(R.id.widget_toggle, action)
            manager.updateAppWidget(id, view)
        }
    }
}
