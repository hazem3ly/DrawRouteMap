package com.linkatapps.drawroutemap;

import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.linkatapps.drawroutemap.map.Constant;
import com.linkatapps.drawroutemap.map.MapUtils;
import com.linkatapps.drawroutemap.map.Route;

import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLoadedCallback {

    private GoogleMap mMap;
    private FloatingActionButton walking_fab, driving_fab;
    private MapUtils mapUtils;
    private boolean mapLoaded;
    private LatLng home, distention;
    private TextView distance_txt, duration_txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        home = new LatLng(29.965206, 31.246640);
        distention = new LatLng(29.995581, 31.231433);

        distance_txt = findViewById(R.id.distance_txt);
        duration_txt = findViewById(R.id.duration_txt);

        walking_fab = findViewById(R.id.walking_fab);
        walking_fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                walkingRoute();
            }
        });
        driving_fab = findViewById(R.id.driving_fab);
        driving_fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drivingRoute();
            }
        });
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void drivingRoute() {
        if (mapLoaded) {
            mapUtils.drawRoute(getLoaderManager(), home.latitude, home.longitude,
                    distention.latitude, distention.longitude,
                    Constant.DRIVING_MODE, Constant.METRIC_UNITS, "en",
                    10, Color.BLUE, MapUtils.SOLID_STYLE, new MapUtils.OnRoutesReadyCallback() {
                        @Override
                        public void onRoutesReady(ArrayList<Route> route) {
                            fillUiData(route.get(0));
                        }
                    });
        }
    }

    private void fillUiData(Route route) {
        if (route != null) {
            distance_txt.setText(String.valueOf(route.distance.text));
            duration_txt.setText(String.valueOf(route.duration.text));
        }
    }

    private void walkingRoute() {
        if (mapLoaded) {
            mapUtils.drawRoute(getLoaderManager(), home.latitude, home.longitude,
                    distention.latitude, distention.longitude,
                    Constant.WALKING_MODE, Constant.METRIC_UNITS, "en",
                    10, Color.RED, MapUtils.DASH_STYLE, new MapUtils.OnRoutesReadyCallback() {
                        @Override
                        public void onRoutesReady(ArrayList<Route> route) {
                            fillUiData(route.get(0));
                        }
                    });
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLoadedCallback(this);
    }

    @Override
    public void onMapLoaded() {
        mapLoaded = true;
        Toast.makeText(this, "Map Loaded Successfully", Toast.LENGTH_SHORT).show();
        mapUtils = new MapUtils(this, mMap, "AIzaSyDMXoxGSVmVtqFijzHD1teUJyaJ8L61aXA");
        mapUtils.zoomToLocation(home, 13, true);
    }
}
