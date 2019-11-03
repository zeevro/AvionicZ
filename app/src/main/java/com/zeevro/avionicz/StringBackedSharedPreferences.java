package com.zeevro.avionicz;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;

public class StringBackedSharedPreferences implements SharedPreferences {
    private SharedPreferences prefs;

    StringBackedSharedPreferences(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public Map<String, ?> getAll() {
        return prefs.getAll();
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        String value = prefs.getString(key, null);
        if ((value == null) || value.isEmpty()) {
            return defValue;
        }
        return value;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        // TODO: Is this good?
        return prefs.getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return prefs.getInt(key, defValue);
        } catch (ClassCastException ignored) { }

        String value = prefs.getString(key, null);
        if ((value == null) || value.isEmpty()) {
            return defValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) { }

        return 0;
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return prefs.getLong(key, defValue);
        } catch (ClassCastException ignored) { }

        String value = prefs.getString(key, null);
        if ((value == null) || value.isEmpty()) {
            return defValue;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) { }

        return 0;
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return prefs.getFloat(key, defValue);
        } catch (ClassCastException ignored) { }

        String value = prefs.getString(key, null);
        if ((value == null) || value.isEmpty()) {
            return defValue;
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException ignored) { }

        return 0f;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return prefs.getBoolean(key, defValue);
        } catch (ClassCastException ignored) { }

        String value = prefs.getString(key, null);
        if ((value == null) || value.isEmpty()) {
            return defValue;
        }
        try {
            return Boolean.valueOf(value);
        } catch (NumberFormatException ignored) { }

        return false;
    }

    @Override
    public boolean contains(String key) {
        return prefs.contains(key);
    }

    @Override
    public Editor edit() {
        return prefs.edit();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
