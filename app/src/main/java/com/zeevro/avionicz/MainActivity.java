package com.zeevro.avionicz;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.WayPoint;

import static android.view.View.*;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener, OnTouchListener, SensorEventListener, LocationListener {

    private static final String TAG = "AvionicZ";

    private static final int GPX_FILE_REQUEST_CODE = 12;

    private class WaypointArrayAdapter extends ArrayAdapter<WayPoint> {
        WaypointArrayAdapter(@NonNull Context context, @LayoutRes int resource) {
            super(context, resource);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, getIntPreference("waypoint_list_font_size", 40));
            view.setWidth(4000);
            WayPoint wpt = getItem(position);
            if (wpt != null) {
                view.setText(wpt.getName());
            }
            return view;
        }
    }

    private SharedPreferences prefs;

    private TextView altView, altDecView, vsiView, bearingView, distanceView;
    private Button pressureButton, waypointButton;

    private float lastAltitude, seaLevelPressureCalibration;
    private long lastAltitudeTimestamp;
    private int seaLevelPressure, tempSeaLevelPressure, seaLevelPressureAdjustSensitivity, vsiColorMax;

    private LowPassFilter altitudeFilter = new LowPassFilter();
    private LowPassFilter vsiFilter = new LowPassFilter();

    private ArrayList<WayPoint> waypoints = new ArrayList<>();

    private ColorGradient vsiGradient;

    private Location currentLocation = null, waypointLocation = new Location("waypoint");

    private BearingArrow bearingArrow;

    protected String getStringPreference(String name, String default_value) {
        String value = prefs.getString(name, null);
        if (value == null || value.isEmpty()) {
            return default_value;
        }
        return value;
    }

    protected float getFloatPreference(String name, float default_value) {
        String value = prefs.getString(name, null);
        if (value == null || value.isEmpty()) {
            return default_value;
        }
        return Float.valueOf(value);
    }

    protected int getIntPreference(String name, int default_value) {
        String value = prefs.getString(name, null);
        if (value == null || value.isEmpty()) {
            return default_value;
        }
        return Integer.valueOf(value);
    }

    protected void loadWayPoints(Uri uri) {
        InputStream stream;
        try {
            stream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found!", e);
            Toast.makeText(this, getString(R.string.cant_open_file), Toast.LENGTH_LONG).show();
            return;
        }

        if (stream == null) {
            Toast.makeText(this, getString(R.string.cant_open_file), Toast.LENGTH_LONG).show();
            return;
        }

        Gpx parsedGpx = null;
        try {
            parsedGpx = new GPXParser().parse(stream);
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "GPX parser failed!", e);
            Toast.makeText(this, getString(R.string.gpx_parser_failed), Toast.LENGTH_LONG).show();
        }

        if (parsedGpx == null) {
            Toast.makeText(this, getString(R.string.gpx_parser_failed), Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<WayPoint> tempWaypoints = new ArrayList<>(parsedGpx.getWayPoints());
        if (tempWaypoints.size() == 0) {
            Toast.makeText(this, getString(R.string.no_waypoints_in_gpx), Toast.LENGTH_LONG).show();
            return;
        }

        waypoints = tempWaypoints;
        waypointLocation.reset();
        waypointButton.setText(R.string.waypoint_button);

        Toast.makeText(this, String.format(getString(R.string.loaded_waypoints), tempWaypoints.size()), Toast.LENGTH_LONG).show();

        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString("gpx_file_uri", uri.toString());
        prefsEditor.apply();
    }

    protected float m2ft(float x) {
        return x * 3.28084f;
    }

    protected float m2mile(float x) {
        return x * 0.000621371f;
    }

    protected float waypointDistance(Location location, WayPoint wpt) {
        Location wptLoc = new Location("waypoint");
        wptLoc.setLatitude(wpt.getLatitude());
        wptLoc.setLongitude(wpt.getLongitude());
        return location.distanceTo(wptLoc);
    }

    protected void setSeaLevelPressure(int newPressure) {
        seaLevelPressure = newPressure;
        SharedPreferences.Editor prefs_editor = prefs.edit();
        prefs_editor.putInt("sea_level_pressure", seaLevelPressure);
        prefs_editor.apply();
        pressureButton.setText(String.valueOf(seaLevelPressure));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        altView = (TextView) findViewById(R.id.altitudeValue);
        altDecView = (TextView) findViewById(R.id.altitudeDecimalValue);
        vsiView = (TextView) findViewById(R.id.verticalSpeedValue);
        pressureButton = (Button) findViewById(R.id.pressureButton);
        waypointButton = (Button) findViewById(R.id.waypointButton);
        bearingView = (TextView) findViewById(R.id.bearingValue);
        distanceView = (TextView) findViewById(R.id.distanceValue);

        pressureButton.setOnClickListener(this);
        pressureButton.setOnTouchListener(this);
        findViewById(R.id.minusTenButton).setOnClickListener(this);
        findViewById(R.id.plusOneButton).setOnClickListener(this);
        findViewById(R.id.minusOneButton).setOnClickListener(this);
        findViewById(R.id.plusTenButton).setOnClickListener(this);
        findViewById(R.id.waypointButton).setOnClickListener(this);

        ImageView arrowView = (ImageView)findViewById(R.id.bearingArrow);
        bearingArrow = new BearingArrow(arrowView);

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, true);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        for (String k : prefs.getAll().keySet()) {
            onSharedPreferenceChanged(prefs, k);
        }
        setSeaLevelPressure(prefs.getInt("sea_level_pressure", (int)SensorManager.PRESSURE_STANDARD_ATMOSPHERE));

        vsiGradient = new ColorGradient(0xFFFF0000, vsiView.getCurrentTextColor(), 0xFF00FF00);

        String gpxFileUri = getStringPreference("gpx_file_uri", null);
        if (gpxFileUri != null) {
            loadWayPoints(Uri.parse(gpxFileUri));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);

        requestPermissions(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, 0);

        bearingArrow.startAnimation();
    }

    @Override
    protected void onStop() {
        super.onStop();

        LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        locationManager.removeUpdates(this);

        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        bearingArrow.stopAnimation();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_gpx_file:
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), GPX_FILE_REQUEST_CODE);
                return true;

            case R.id.action_reset_sea_level_pressure:
                setSeaLevelPressure((int)SensorManager.PRESSURE_STANDARD_ATMOSPHERE);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_PRESSURE:
                float pressureValue = sensorEvent.values[0];

                float altitude = altitudeFilter.getOutput(m2ft(SensorManager.getAltitude(seaLevelPressure + seaLevelPressureCalibration, pressureValue)));

                float vsi = vsiFilter.getOutput((altitude - lastAltitude) / (sensorEvent.timestamp - lastAltitudeTimestamp));
                lastAltitude = altitude;
                lastAltitudeTimestamp = sensorEvent.timestamp;
                int verticalSpeed = (int)(vsi * 60000000000L);

                altView.setText(String.valueOf((int)altitude));
                altDecView.setText(getString(R.string.decimal_separator) + Math.abs((int)(altitude * 10 % 10)));
                vsiView.setText(String.valueOf(verticalSpeed));
                vsiView.setTextColor(vsiGradient.colorForValue(verticalSpeed / (float)vsiColorMax));
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.minusOneButton:
                setSeaLevelPressure(seaLevelPressure - 1); break;
            case R.id.minusTenButton:
                setSeaLevelPressure(seaLevelPressure - 10); break;
            case R.id.plusOneButton:
                setSeaLevelPressure(seaLevelPressure + 1); break;
            case R.id.plusTenButton:
                setSeaLevelPressure(seaLevelPressure + 10); break;

            case R.id.waypointButton:
                final Dialog waypointPicker = new Dialog(this);
                waypointPicker.setContentView(R.layout.waypoint_picker);

                ListView waypointsListView = waypointPicker.findViewById(R.id.waypoints_list);
                WaypointArrayAdapter waypointsAdapter = new WaypointArrayAdapter(waypointPicker.getContext(), android.R.layout.select_dialog_item);
                if (currentLocation != null) {
                    //noinspection Since15
                    waypoints.sort(new Comparator<WayPoint>() {
                        @Override
                        public int compare(WayPoint left, WayPoint right) {
                            return (int)(waypointDistance(currentLocation, left) - waypointDistance(currentLocation, right));
                        }
                    });
                }
                for (WayPoint wpt : waypoints) {
                    waypointsAdapter.add(wpt);
                }
                waypointsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        WayPoint wpt = (WayPoint)parent.getItemAtPosition(position);
                        waypointButton.setText(wpt.getName());
                        waypointLocation.setLatitude(wpt.getLatitude());
                        waypointLocation.setLongitude(wpt.getLongitude());
                        waypointPicker.dismiss();
                    }
                });
                waypointsListView.setAdapter(waypointsAdapter);

                waypointPicker.findViewById(R.id.waypoints_cancel_button).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        waypointLocation.reset();
                        waypointButton.setText(R.string.waypoint_button);
                        waypointPicker.dismiss();
                    }
                });

                waypointPicker.show();
                break;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent evt) {
        switch (view.getId()) {
            case R.id.pressureButton:
                if (evt.getAction() == MotionEvent.ACTION_MOVE) {
                    tempSeaLevelPressure = (int)(seaLevelPressure - evt.getY() / seaLevelPressureAdjustSensitivity) + 1;
                    pressureButton.setText(String.valueOf(tempSeaLevelPressure));
                    return true;
                }
                if (evt.getAction() == MotionEvent.ACTION_UP) {
                    Rect rect = new Rect();
                    view.getHitRect(rect);
                    if (!rect.contains(view.getLeft() + (int)evt.getX(), view.getTop() + (int)evt.getY())) {
                        setSeaLevelPressure(tempSeaLevelPressure);
                        return true;
                    }
                }
                break;
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String k) {
        Log.d(TAG, "onSharedPreferenceChanged: " + k + " = " + prefs.getAll().get(k));
        switch (k) {
            case "altitude_low_pass_alpha":
                altitudeFilter.setAlpha(getFloatPreference("altitude_low_pass_alpha", 0.25f)); break;
            case "vertical_speed_low_pass_alpha":
                vsiFilter.setAlpha(getFloatPreference("vertical_speed_low_pass_alpha", 0.05f)); break;
            case "sea_level_pressure_adjust_sensitivity":
                seaLevelPressureAdjustSensitivity = getIntPreference("sea_level_pressure_adjust_sensitivity", 130); break;
            case "sea_level_pressure_calibration":
                seaLevelPressureCalibration = getFloatPreference("sea_level_pressure_calibration", 0);
            case "vertical_speed_color_max":
                vsiColorMax = getIntPreference("vertical_speed_color_max", 100); break;
        }
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        int result = grantResults[0];
        if (result == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (SecurityException ex) {
                Toast.makeText(this, "GPS permission denied!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;

        if (waypointLocation.getLatitude() == 0 && waypointLocation.getLongitude() == 0) {
            bearingView.setText("");
            distanceView.setText("");
            return;
        }

        float b = (location.bearingTo(waypointLocation) - location.getBearing() + 360) % 360;
        bearingView.setText(String.format(getString(R.string.waypoint_angle), (int)b));
        distanceView.setText(String.format(getString(R.string.waypoint_distance), m2mile(location.distanceTo(waypointLocation))));
        bearingArrow.setAngleDegrees(b);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        // TODO: Use this to put up a little accuracy/availability flag on the GUI.
        Log.d(TAG, "Status: " + s + ", " + i);
    }

    @Override
    public void onProviderEnabled(String s) {
        Log.d(TAG, "Provider Enabled: " + s);
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d(TAG, "Provider Disabled: " + s);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        switch (requestCode) {
            case GPX_FILE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    Uri uri = resultData.getData();
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    loadWayPoints(uri);
                }
                break;
        }
    }

}
