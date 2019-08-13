package com.zeevro.avionicz;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends AppCompatPreferenceActivity implements Preference.OnPreferenceClickListener {

    private static final String TAG = "AvionicZ/Settings";

    private static final int GPX_FILE_REQUEST_CODE = 12;

    public SettingsActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            findPreference("gpx_file_uri").setOnPreferenceClickListener((SettingsActivity)getActivity());
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), GPX_FILE_REQUEST_CODE);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode != GPX_FILE_REQUEST_CODE) return;

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Uri uri = resultData.getData();
            Log.d(TAG, "Got GPX file URI: " + uri);
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SharedPreferences.Editor prefsEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                prefsEditor.putString("gpx_file_uri", uri.toString());
                prefsEditor.apply();
            }
        }
    }
}
