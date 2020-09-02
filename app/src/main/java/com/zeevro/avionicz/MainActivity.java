package com.zeevro.avionicz;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
//import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.WayPoint;

import static android.view.View.OnClickListener;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnClickListener, SensorEventListener, LocationListener {

    private static final String TAG = "AvionicZ/Main";

    private class WaypointArrayAdapter extends ArrayAdapter<WayPoint> {
        WaypointArrayAdapter(Context context, int resource, List<WayPoint> objects) {
            super(context, resource, objects);
        }

        @SuppressWarnings("ConstantConditions")
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            final LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View myView = inflater.inflate(R.layout.waypoint_entry, parent, false);
            WayPoint wpt = getItem(position);

            TextView textView = myView.findViewById(R.id.waypoint_entry_text);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt("waypoint_list_font_size", 40));
            textView.setText(wpt.getName());
            textView.setWidth(parent.getWidth());

            ImageView imageView = myView.findViewById(R.id.waypoint_entry_image);
            if (currentLocation != null) {
                imageView.setImageDrawable(arrowDrawable);
                imageView.setRotation(waypointBearing(currentLocation, wpt));
                imageView.setMaxHeight((int)textView.getTextSize());
            }

            return myView;
        }
    }

    private StringBackedSharedPreferences prefs;

    private TextView altView, altDecView, /*vsiView,*/ bearingView, distanceView, etaView, headingView, pressureView, waypointView/*, debugView*/;

    private float lastPressure, seaLevelPressureCalibration/*, slipZero, slipCoefficient*/;
    private long lastPressureTimestamp;
    private int seaLevelPressure/*, vsiColorMax*/;
//    private boolean resetHorizon = false;
//    private float[] horizon = new float[3];

    private final LowPassFilter pressureFilter = new LowPassFilter();
    private final LowPassFilter vsiFilter = new LowPassFilter();
    private final LowPassFilter slipFilter = new LowPassFilter();

    private ArrayList<WayPoint> waypoints = new ArrayList<>();

//    private ColorGradient vsiGradient;

    private Location currentLocation = null;
    private final Location waypointLocation = new Location("waypoint");

//    private ArtificialHorizon artificialHorizon;
    private BearingArrow bearingArrow;
    private VerticalSpeedGauge vsiGauge;

    private Drawable arrowDrawable;

    private boolean havePressureSensor = false;

    protected void loadWayPoints(Uri uri) {
        // TODO: Use GPXParser().parse(stream, new GpxFetchedAndParsed() { ... });
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
        waypointView.setText("");

        Toast.makeText(this, String.format(getString(R.string.loaded_waypoints), tempWaypoints.size()), Toast.LENGTH_LONG).show();
    }

    protected float m2ft(float x) {
        return x * 3.28084f;
    }

    protected float m2mile(float x) {
        return x * 0.000621371f;
    }

    protected @NonNull Location locationFromWaypoint(@NonNull WayPoint wpt) {
        Location wptLoc = new Location("waypoint");
        wptLoc.setLatitude(wpt.getLatitude());
        wptLoc.setLongitude(wpt.getLongitude());
        return wptLoc;
    }

    protected float waypointDistance(@NonNull Location location, @NonNull WayPoint wpt) {
        return location.distanceTo(locationFromWaypoint(wpt));
    }

    protected float waypointBearing(@NonNull Location location, @NonNull WayPoint wpt) {
        return location.bearingTo(locationFromWaypoint(wpt)) - location.getBearing();
    }

    protected void setSeaLevelPressure(int newPressure) {
        seaLevelPressure = newPressure;
        SharedPreferences.Editor prefs_editor = prefs.edit();
        prefs_editor.putInt("sea_level_pressure", seaLevelPressure);
        prefs_editor.apply();
        pressureView.setText(String.valueOf(seaLevelPressure));
    }

    @SuppressLint("SetTextI18n")
    protected void setAltitudeIndicator(float altitude) {
        altView.setText(String.valueOf((int)altitude));
        altDecView.setText(getString(R.string.decimal_separator) + Math.abs((int)(altitude * 10 % 10)));
    }

    protected int calcEta(float speed, float distance) {
        if (speed == 0) return 0;
        return (int)(distance / speed);
    }

    protected String getEtaString(int eta) {
        if (eta >= 3600) return String.format(getString(R.string.eta_hours), eta / 3600, eta % 3600 / 60, eta % 3600 % 60);
        if (eta >= 60) return String.format(getString(R.string.eta_minutes), eta / 60, eta % 60);
        return String.format(getString(R.string.eta_seconds), eta);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_screen);

        altView = findViewById(R.id.altitudeValue);
        altDecView = findViewById(R.id.altitudeDecimalValue);
//        vsiView = findViewById(R.id.verticalSpeedValue);
        pressureView = findViewById(R.id.pressureValue);
        waypointView = findViewById(R.id.waypointText);
        bearingView = findViewById(R.id.bearingValue);
        distanceView = findViewById(R.id.distanceValue);
        etaView = findViewById(R.id.etaValue);
        headingView = findViewById(R.id.headingValue);
//        debugView = findViewById(R.id.distanceValue);

        havePressureSensor = getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER);

//        ImageView horizonView = findViewById(R.id.vsi);
//        artificialHorizon = new ArtificialHorizon(horizonView);
//        horizonView.setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                resetHorizon = true;
//                return true;
//            }
//        });

        ImageView vsiGaugeView = findViewById(R.id.vsiGauge);
        vsiGauge = new VerticalSpeedGauge(vsiGaugeView);

        ImageView arrowView = findViewById(R.id.bearingArrow);
        bearingArrow = new BearingArrow(arrowView);

        arrowDrawable = getResources().getDrawable(R.drawable.arrow);

        prefs = ((MyApp)getApplication()).prefs;
        prefs.registerOnSharedPreferenceChangeListener(this);
        for (String k : prefs.getAll().keySet()) {
            onSharedPreferenceChanged(prefs, k);
        }
        setSeaLevelPressure(prefs.getInt("sea_level_pressure", (int)SensorManager.PRESSURE_STANDARD_ATMOSPHERE));

//        vsiGradient = new ColorGradient(Color.RED, vsiView.getCurrentTextColor(), Color.GREEN);

        String gpxFileUri = prefs.getString("gpx_file_uri", null);
        if (gpxFileUri != null) {
            loadWayPoints(Uri.parse(gpxFileUri));
        }

        if (!havePressureSensor) {
//            vsiView.setText("---");
            Toast.makeText(this, "No barometer found! Using GPS altitude.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

//        artificialHorizon.setAttitude(0, 0);

        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            if (havePressureSensor) {
                Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
            }

            Sensor orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI);

            Sensor slipSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, slipSensor, SensorManager.SENSOR_DELAY_UI);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, 0);
        } else {
            onRequestPermissionsResult(0, new String[]{}, new int[]{PackageManager.PERMISSION_GRANTED});
        }

        vsiGauge.startAnimation();
        bearingArrow.startAnimation();
    }

    @Override
    protected void onStop() {
        super.onStop();

        LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        locationManager.removeUpdates(this);

        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        vsiGauge.stopAnimation();
        bearingArrow.stopAnimation();
    }

/*
    private String angleVectStr(float[] vect) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            stringBuilder.append(' ');
            stringBuilder.append((int)Math.toDegrees(vect[i]));
        }
        return stringBuilder.substring(1);
    }

    private String vectStr(float[] vect) {
        StringBuilder stringBuilder = new StringBuilder();
        for (float n : vect) {
            stringBuilder.append(' ');
            stringBuilder.append(String.format("%.4f", n));
        }
        return stringBuilder.substring(1);
    }

    private float[] quaternion(float[] values) {
        float theta_over_two = (float)Math.acos(values[3]);
        float sin_theta_over_2 = (float)Math.sin(theta_over_two);

        return new float[]{
                2 * theta_over_two,
                values[0] / sin_theta_over_2,
                values[1] / sin_theta_over_2,
                values[2] / sin_theta_over_2
        };
    }
*/

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_PRESSURE:
                float pressure = pressureFilter.getOutput(sensorEvent.values[0]);

                float altitude = m2ft(SensorManager.getAltitude(seaLevelPressure + seaLevelPressureCalibration, pressure));

                float vsi = vsiFilter.getOutput((altitude - m2ft(SensorManager.getAltitude(seaLevelPressure + seaLevelPressureCalibration, lastPressure))) / (sensorEvent.timestamp - lastPressureTimestamp));
                lastPressure = pressure;
                lastPressureTimestamp = sensorEvent.timestamp;
                int verticalSpeed = (int) (vsi * 60000000000L);

                setAltitudeIndicator(altitude);

//                vsiView.setText(String.valueOf(verticalSpeed));
//                vsiView.setTextColor(vsiGradient.colorForValue(verticalSpeed / (float) vsiColorMax));

                vsiGauge.setVerticalSpeed(verticalSpeed);

                break;


//            case Sensor.TYPE_ROTATION_VECTOR:
////                StringBuilder debugStr = new StringBuilder();
//
////                debugStr.append("A: ");
////                debugStr.append(vectStr(quaternion(sensorEvent.values)));
//
//                float[] rotationMatrix = new float[16];
//                SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
//
//                final int worldAxisForDeviceAxisX = SensorManager.AXIS_X;
//                final int worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
//
//                float[] adjustedRotationMatrix = new float[16];
//                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX, worldAxisForDeviceAxisY, adjustedRotationMatrix);
//
//                float[] rotation = new float[3];
//                SensorManager.getOrientation(adjustedRotationMatrix, rotation);
//
////                debugStr.append("\nB: ");
////                debugStr.append(angleVectStr(rotation));
//
//                if (resetHorizon) {
//                    for (int i = 0; i < 3; i++) {
//                        horizon[i] = rotation[i];
//                    }
//                    resetHorizon = false;
//                }
//
////                debugStr.append("\nC: ");
////                debugStr.append(angleVectStr(rotation));
//
//                float pitch = -(float)Math.toDegrees(rotation[1] - horizon[1]);
//                float roll = -(float)Math.toDegrees(rotation[2] - horizon[2]);
//
////                artificialHorizon.setAttitude(pitch, roll);
//
////                debugView.setText(debugStr.toString());
//
//                break;
//
//            case Sensor.TYPE_ACCELEROMETER:
//                float slipValue = slipFilter.getOutput(sensorEvent.values[0]);
//                if (resetHorizon) {
//                    slipZero = slipValue;
//                }
////                artificialHorizon.setSlip((slipZero - slipValue) * slipCoefficient);
//                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.settingsButton:
                startActivity(new Intent(this, SettingsActivity.class)); break;

            case R.id.altitudeValue:
            case R.id.altitudeDecimalValue:
            case R.id.verticalSpeedValue:
            case R.id.pressureValue:
            case R.id.vsiGauge:
                final Dialog pressureWindow = new Dialog(this);
                pressureWindow.setContentView(R.layout.pressure_window);
                //noinspection ConstantConditions
                pressureWindow.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

                final AtomicBoolean isEditing = new AtomicBoolean(false);
                final EditText pressureWindowText = pressureWindow.findViewById(R.id.pressureEdit);

                pressureWindowText.setText(String.format(Locale.getDefault(), "%d", seaLevelPressure));

                pressureWindow.findViewById(R.id.buttonCancel).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        pressureWindow.dismiss();
                    }
                });

                pressureWindow.findViewById(R.id.buttonStandard).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setSeaLevelPressure((int)SensorManager.PRESSURE_STANDARD_ATMOSPHERE);
                        pressureWindow.dismiss();
                    }
                });

                pressureWindow.findViewById(R.id.buttonOk).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setSeaLevelPressure(Integer.parseInt(pressureWindowText.getText().toString()));
                        pressureWindow.dismiss();
                    }
                });

                OnClickListener keypadOnClick = new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!isEditing.getAndSet(true)) {
                            pressureWindowText.setText("");
                        }
                        pressureWindowText.append(((Button)view).getText());
                    }
                };

                pressureWindow.findViewById(R.id.button0).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button1).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button2).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button3).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button4).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button5).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button6).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button7).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button8).setOnClickListener(keypadOnClick);
                pressureWindow.findViewById(R.id.button9).setOnClickListener(keypadOnClick);

                pressureWindow.findViewById(R.id.buttonMinus).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        pressureWindowText.setText(String.format(Locale.getDefault(), "%d", Integer.parseInt(pressureWindowText.getText().toString()) - 1));
                        isEditing.set(false);
                    }
                });

                pressureWindow.findViewById(R.id.buttonPlus).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        pressureWindowText.setText(String.format(Locale.getDefault(), "%d", Integer.parseInt(pressureWindowText.getText().toString()) + 1));
                        isEditing.set(false);
                    }
                });

                pressureWindow.show();
                break;

            case R.id.bearingArrow:
            case R.id.bearingValue:
            case R.id.etaValue:
            case R.id.distanceValue:
            case R.id.headingValue:
            case R.id.waypointText:
                final Dialog waypointPicker = new Dialog(this);
                waypointPicker.setContentView(R.layout.waypoint_picker);

                final ListView waypointsListView = waypointPicker.findViewById(R.id.waypoints_list);
                if (currentLocation != null) {
                    //noinspection Since15
                    Collections.sort(waypoints, new Comparator<WayPoint>() {
                        @Override
                        public int compare(WayPoint left, WayPoint right) {
                            return (int)(waypointDistance(currentLocation, left) - waypointDistance(currentLocation, right));
                        }
                    });
                }
                final WaypointArrayAdapter waypointsAdapter = new WaypointArrayAdapter(waypointPicker.getContext(), android.R.layout.select_dialog_item, new ArrayList<>(waypoints));
                waypointsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        WayPoint wpt = (WayPoint)parent.getItemAtPosition(position);
                        waypointView.setText(wpt.getName());
                        waypointLocation.setLatitude(wpt.getLatitude());
                        waypointLocation.setLongitude(wpt.getLongitude());
                        waypointPicker.dismiss();
                    }
                });
                waypointsListView.setAdapter(waypointsAdapter);

                ((EditText)waypointPicker.findViewById(R.id.waypoints_search)).addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        String filter = charSequence.toString().toLowerCase();
                        waypointsAdapter.clear();
                        for (WayPoint wp : waypoints) {
                            Log.d(TAG, wp.getName().toLowerCase());
                            if (wp.getName().toLowerCase().contains(filter)) {
                                waypointsAdapter.add(wp);
                            }
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { }
                });

                waypointPicker.findViewById(R.id.waypoints_clear_button).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        waypointLocation.reset();
                        waypointView.setText("");
                        distanceView.setText("");
                        etaView.setText("");
                        bearingView.setText("");
                        bearingArrow.setAngle(0);
                        waypointPicker.dismiss();
                    }
                });

                waypointPicker.findViewById(R.id.waypoints_cancel_button).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        waypointPicker.dismiss();
                    }
                });

                waypointPicker.show();
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String k) {
        Log.d(TAG, "onSharedPreferenceChanged: " + k + " = " + prefs.getAll().get(k));
        switch (k) {
            case "slip_low_pass_alpha":
                slipFilter.setAlpha(prefs.getFloat("slip_low_pass_alpha", 0.12f)); break;
//            case "slip_coefficient":
//                slipCoefficient = prefs.getFloat("slip_coefficient", 0.5f); break;
            case "altitude_low_pass_alpha":
                pressureFilter.setAlpha(prefs.getFloat("altitude_low_pass_alpha", 0.25f)); break;
            case "vertical_speed_low_pass_alpha":
                vsiFilter.setAlpha(prefs.getFloat("vertical_speed_low_pass_alpha", 0.05f)); break;
            case "sea_level_pressure_calibration":
                seaLevelPressureCalibration = prefs.getFloat("sea_level_pressure_calibration", 0);
//            case "vertical_speed_color_max":
//                vsiColorMax = prefs.getInt("vertical_speed_color_max", 100); break;
            case "gpx_file_uri":
                String gpxFileUri = prefs.getString("gpx_file_uri", null);
                if (gpxFileUri != null) {
                    loadWayPoints(Uri.parse(gpxFileUri));
                }
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

        if (!havePressureSensor) {
            setAltitudeIndicator((float)location.getAltitude());
        }

        headingView.setText(String.format(getString(R.string.angle), (int)location.getBearing()));

        if (waypointLocation.getLatitude() == 0 && waypointLocation.getLongitude() == 0) {
            bearingView.setText("");
            distanceView.setText("");
        } else {
            float bearingToWaypoint = (location.bearingTo(waypointLocation) - location.getBearing() + 4 * 360) % 360;
            float distanceToWaypoint = location.distanceTo(waypointLocation);

            bearingView.setText(String.format(getString(R.string.angle), (int)bearingToWaypoint));
            distanceView.setText(String.format(getString(R.string.waypoint_distance), m2mile(distanceToWaypoint)));
            etaView.setText(getEtaString(calcEta(location.getSpeed(), distanceToWaypoint)));
            bearingArrow.setAngleDegrees(bearingToWaypoint);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider Enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d(TAG, "Provider Disabled: " + s);
    }
}