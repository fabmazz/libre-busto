/*
    BusTO Activities
    Copyright (C) 2020 Andrea Ugo e Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package it.reyboz.bustorino;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import it.reyboz.bustorino.middleware.GeneralActivity;
import it.reyboz.bustorino.data.NextGenDB;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.*;

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.map.CustomInfoWindow;

public class ActivityMap extends GeneralActivity {

    private static final String TAG = "Busto-MapActivity";
    private static final String MAP_CURRENT_ZOOM_KEY = "map-current-zoom";
    private static final String MAP_CENTER_LAT_KEY = "map-center-lat";
    private static final String MAP_CENTER_LON_KEY = "map-center-lon";

    public static final String BUNDLE_LATIT = "lat";
    public static final String BUNDLE_LONGIT = "lon";
    public static final String BUNDLE_NAME = "name";
    public static final String BUNDLE_ID = "ID";

    private static final double DEFAULT_CENTER_LAT = 45.0708;
    private static final double DEFAULT_CENTER_LON = 7.6858;
    private static final double POSITION_FOUND_ZOOM = 18.3;

    private HashSet<String> shownStops = null;


    private MapView map = null;
    public Context ctx;
    private MyLocationNewOverlay mLocationOverlay = null;
    private FolderOverlay stopsFolderOverlay = null;
    protected ImageButton btCenterMap;
    protected ImageButton btFollowMe;

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //handle permissions first, before map is created. not depicted here

        //load/initialize the osmdroid configuration


        ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string

        //inflate and create the map
        setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        //map.setTilesScaledToDpi(true);
        map.setFlingEnabled(true);

        // add ability to zoom with 2 fingers
        map.setMultiTouchControls(true);

        btCenterMap = (ImageButton) findViewById(R.id.ic_center_map);
        btFollowMe = (ImageButton) findViewById(R.id.ic_follow_me);

        //setup FolderOverlay
        stopsFolderOverlay = new FolderOverlay();

        // take the parameters if it's called from other Activities
        Bundle b = getIntent().getExtras();

        startMap(b, savedInstanceState);

        // on drag and zoom reload the markers
        map.addMapListener(new DelayedMapListener(new MapListener() {

            @Override
            public boolean onScroll(ScrollEvent paramScrollEvent) {
                loadMarkers();
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                loadMarkers();
                return true;
            }

        }));


        btCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                final GeoPoint myPosition = mLocationOverlay.getMyLocation();
                map.getController().animateTo(myPosition);
            }
        });


        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btFollowMe clicked ");
                if (!mLocationOverlay.isFollowLocationEnabled()) {
                    mLocationOverlay.enableFollowLocation();
                    btFollowMe.setImageResource(R.drawable.ic_follow_me_on);
                } else {
                    mLocationOverlay.disableFollowLocation();
                    btFollowMe.setImageResource(R.drawable.ic_follow_me);
                }
            }
        });

    }


    public void startMap(Bundle incoming, Bundle savedInstanceState) {
        //parse incoming bundle
        GeoPoint marker = null;
        String name = null;
        String ID = null;
        if (incoming != null) {
            double lat = incoming.getDouble(BUNDLE_LATIT);
            double lon = incoming.getDouble(BUNDLE_LONGIT);
            marker = new GeoPoint(lat, lon);
            name = incoming.getString(BUNDLE_NAME);
            ID = incoming.getString(BUNDLE_ID);
        }

        shownStops = new HashSet<>();
        // move the map on the marker position or on a default view point: Turin, Piazza Castello
        // and set the start zoom
        IMapController mapController = map.getController();
        GeoPoint startPoint = null;

        boolean havePositionPermission = true;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askForPermissionIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_REQUEST_POSITION);
            havePositionPermission = false;
        }

        if (marker != null) {
            startPoint = marker;
            mapController.setZoom(POSITION_FOUND_ZOOM);
        } else if (savedInstanceState != null || !havePositionPermission) {
            mapController.setZoom(savedInstanceState.getDouble(MAP_CURRENT_ZOOM_KEY));
            mapController.setCenter(new GeoPoint(savedInstanceState.getDouble(MAP_CENTER_LAT_KEY),
                    savedInstanceState.getDouble(MAP_CENTER_LON_KEY)));
        } else {
            boolean found = false;
            LocationManager locationManager =
                    (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {

                Location userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (userLocation != null) {
                    mapController.setZoom(POSITION_FOUND_ZOOM);
                    startPoint = new GeoPoint(userLocation);
                    found = true;
                }
            }
            if(!found){
                startPoint = new GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON);
                mapController.setZoom(16.0);
            }
        }

        // set the minimum zoom level
        map.setMinZoomLevel(15.0);
        //add contingency check (shouldn't happen..., but)
        if (startPoint != null) {
            mapController.setCenter(startPoint);
        }

        // Location Overlay
        // from OpenBikeSharing (THANK GOD)
        GpsMyLocationProvider imlp = new GpsMyLocationProvider(this.getBaseContext());
        imlp.setLocationUpdateMinDistance(5);
        imlp.setLocationUpdateMinTime(2000);
        this.mLocationOverlay = new MyLocationNewOverlay(imlp,map);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        btFollowMe.setImageResource(R.drawable.ic_follow_me_on);
        mLocationOverlay.setOptionsMenuEnabled(true);
        /*
        mLocationOverlay.runOnFirstFix(() -> {
            mapController.setCenter(mLocationOverlay.getMyLocation());
            mapController.animateTo(mLocationOverlay.getMyLocation());
        });
         */

        map.getOverlays().add(this.mLocationOverlay);

        //add stops overlay
        map.getOverlays().add(this.stopsFolderOverlay);


        loadMarkers();
        if (marker != null) {
            // make a marker with the info window open for the searched marker
            makeMarker(startPoint, name , ID, true);
        }

    }

    public Marker makeMarker(GeoPoint geoPoint, String stopName, String ID, boolean isStartMarker) {

        // add a marker
        Marker marker = new Marker(map);

        // set custom info window as info window
        CustomInfoWindow popup = new CustomInfoWindow(map, ID, stopName);
        marker.setInfoWindow(popup);

        // make the marker clickable
        marker.setOnMarkerClickListener((thisMarker, mapView) -> {
            if (thisMarker.isInfoWindowOpen()) {
                // on second click

                // create an intent with these extras
                Intent intent = new Intent(ActivityMap.this, ActivityMain.class);
                Bundle b = new Bundle();
                b.putString("bus-stop-ID", ID);
                b.putString("bus-stop-display-name", stopName);
                intent.putExtras(b);
                intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

                // start ActivityMain with the previous intent
                startActivity(intent);
            } else {
                // on first click

                // hide all opened info window
                InfoWindow.closeAllInfoWindowsOn(map);
                // show this particular info window
                thisMarker.showInfoWindow();
                // move the map to its position
                map.getController().animateTo(thisMarker.getPosition());
            }

            return true;
        });

        // set its position
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // add to it an icon
        marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));
        // add to it a title
        marker.setTitle(stopName);
        // set the description as the ID
        marker.setSnippet(ID);

        // show popup info window of the searched marker
        if (isStartMarker) {
            marker.showInfoWindow();
        }

        return marker;
    }

    public void loadMarkers() {

        // get rid of the previous markers
        //map.getOverlays().clear();
        //stopsFolderOverlay = new FolderOverlay();
        List<Overlay> stopsOverlays = stopsFolderOverlay.getItems();
        /*if (stopsOverlays != null){
            stopsOverlays.clear();
        }*/

        // get the top, bottom, left and right screen's coordinate
        BoundingBox bb = map.getBoundingBox();
        double latFrom = bb.getLatSouth();
        double latTo = bb.getLatNorth();
        double lngFrom = bb.getLonWest();
        double lngTo = bb.getLonEast();

        // get the stops located in those coordinates
        /*
        StopsDB stopsDB = new StopsDB(ctx);
        stopsDB.openIfNeeded();
        Stop[] stops = stopsDB.queryAllInsideMapView(latFrom, latTo, lngFrom, lngTo);
        stopsDB.closeIfNeeded();
        */

        NextGenDB dbHelper = new NextGenDB(ctx);
        Stop[] stops = dbHelper.queryAllInsideMapView(latFrom, latTo, lngFrom, lngTo);

        // add new markers of those stops
        for (Stop stop : stops) {
            if (shownStops.contains(stop.ID)){
                continue;
            }
            try{
                stop.getLatitude();
                stop.getLongitude();
            } catch (NullPointerException e) {
                Log.e(TAG,"Stop "+stop.ID+ " gives null coordinates");
                e.printStackTrace();
                continue;
            }

            shownStops.add(stop.ID);
            GeoPoint marker = new GeoPoint(stop.getLatitude(), stop.getLongitude());
            Marker stopMarker = makeMarker(marker, stop.getStopDefaultName(), stop.ID, false);
            stopsFolderOverlay.add(stopMarker);

        }

    }

    protected boolean detachMapFromPosition(){
        if (mLocationOverlay.isFollowLocationEnabled()) {
            mLocationOverlay.disableFollowLocation();
            btFollowMe.setImageResource(R.drawable.ic_follow_me);
            return true;
        } return false;
    }

    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.enableMyLocation();
    }

    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.disableMyLocation();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(MAP_CURRENT_ZOOM_KEY, map.getZoomLevelDouble());
        outState.putDouble(MAP_CENTER_LAT_KEY, map.getMapCenter().getLatitude());
        outState.putDouble(MAP_CENTER_LON_KEY, map.getMapCenter().getLongitude());
    }

    /**
     * PERMISSION STUFF
     **/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_POSITION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setOption(LOCATION_PERMISSION_GIVEN, true);
                    //if we sent a request for a new NearbyStopsFragment


                } else {
                    //permission denied
                    setOption(LOCATION_PERMISSION_GIVEN, false);
                }
                break;
            //add other cases for permissions
        }

    }
}