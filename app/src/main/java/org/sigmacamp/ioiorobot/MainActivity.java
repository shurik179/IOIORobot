package org.sigmacamp.ioiorobot;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import ioio.lib.util.android.IOIOActivity;
import ioio.lib.util.IOIOLooper;
import android.content.Context;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends IOIOActivity {
    public ToggleButton startButton;
    public TextView message;
    private IOIOLooper IOIOcontroller;
    public Gps gps;
    public Compass compass;
    private Timer autoUpdate;
    public Location homeLocation;


    /**
     * Called when the activity is first created. Here we normally initialize
     * our GUI.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //define buttons and othr GUI elements
        startButton = (ToggleButton) findViewById(R.id.startButton);
        message=(TextView) findViewById(R.id.textView);
        //create new gps
        gps=new Gps(this);
        // create new compass
        compass=new Compass(this);
    }
    @Override
    public void onResume() {
        super.onResume();
        autoUpdate = new Timer();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        updateDisplay();
                    }
                });
            }
        }, 0, 1000); // updates each 1 sec
        gps.resume();
    }
    @Override
    public void onPause() {
        autoUpdate.cancel();
        super.onPause();
        gps.pause();
    }
    /**
     * create a new IOIO Thread
     */
    @Override
    protected IOIOLooper createIOIOLooper() {
        IOIOcontroller = new IOIOThread(this);
        return IOIOcontroller;
    }

    /*
     *  Create a pop-up ('toast') notification
     *
     */
    public void popup(final String message) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    public void updateDisplay() {
        double latitude=0, longitude=0;
        float distance=0, angle_error=0;
        String s;
        if (startButton.isChecked()) {
            startButton.setBackgroundColor(Color.RED);
        } else {
            startButton.setBackgroundColor(Color.GREEN);
        }

        if (gps.hasLocation()) {
            latitude = gps.getLatitude();
            longitude = gps.getLongitude();
            if (homeLocation!=null){
                distance=gps.distanceTo(homeLocation);
                angle_error=gps.bearingTo(homeLocation)-compass.getAzimut();
                if (angle_error<-180) {
                    angle_error+=360;
                } else if  (angle_error>180) {
                    angle_error-=360;
                }
            }
        }
        s= String.format("Longitude: %.6f\n Latitude: %.6f\n Dist. to home: %.1f\n angle error: %.1f",
                longitude, latitude, distance, angle_error);
        message.setText(s);
    }
    public void setHomeLocation(View v){
        if (gps.hasLocation()) {
            homeLocation=gps.getLocation();
            popup("Home location set!");
        }
    }
}
