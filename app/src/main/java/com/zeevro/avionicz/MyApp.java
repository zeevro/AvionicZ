package com.zeevro.avionicz;

import android.app.Application;
import androidx.preference.PreferenceManager;

import net.danlew.android.joda.JodaTimeAndroid;

public class MyApp extends Application {
    public StringBackedSharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();

        JodaTimeAndroid.init(this);

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, true);
        prefs = new StringBackedSharedPreferences(PreferenceManager.getDefaultSharedPreferences(this));
    }
}
