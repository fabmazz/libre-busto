/*
	BusTO ("backend" components)
    Copyright (C) 2016 Ludovico Pavesi

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

package it.reyboz.bustorino.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsDBInterface;


public class StopsDB extends SQLiteAssetHelper implements StopsDBInterface {
    private static String QUERY_TABLE_stops = "stops";
    private static String QUERY_WHERE_ID = "ID = ?";
    private static String QUERY_WHERE_LAT_AND_LNG_IN_RANGE = "lat >= ? AND lat <= ? AND lon >= ? AND lon <= ?";
    private static String[] QUERY_COLUMN_name = {"name"};
    private static final String[] QUERY_COLUMN_location = {"location"};
    private static final String[] QUERY_COLUMN_route = {"route"};
    private static final String[] QUERY_COLUMN_everything = {"name", "location", "type", "lat", "lon"};
    private static final String[] QUERY_COLUMN_everything_and_ID = {"ID", "name", "location", "type", "lat", "lon"};

    private static String DB_NAME = "stops.sqlite";
    private static int DB_VERSION = 1;
    private SQLiteDatabase db;
    private AtomicInteger openCounter = new AtomicInteger();

    public StopsDB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        // WARNING: do not remove the following line, do not save anything in this database, it will be overwritten on every update!
        setForcedUpgrade();

        // remove old database (BusTo version 1.8.5 and below)
        File filename = new File(context.getFilesDir(), "busto.sqlite");
        if(filename.exists()) {
            //noinspection ResultOfMethodCallIgnored
            filename.delete();
        }
    }

    /**
     * Through the magic of an atomic counter, the database gets opened and closed without race
     * conditions between threads (HOPEFULLY).
     *
     * @return database or null if cannot be opened
     */
    @Nullable
    public synchronized SQLiteDatabase openIfNeeded() {
        openCounter.incrementAndGet();
        this.db = getReadableDatabase();
        return this.db;
    }

    /**
     * Through the magic of an atomic counter, the database gets really closed only when no thread
     * is using it anymore (HOPEFULLY).
     */
    public synchronized void closeIfNeeded() {
        // is anybody still using the database or can we close it?
        if(openCounter.decrementAndGet() <= 0) {
            super.close();
            this.db = null;
        }
    }

    public List<String> getRoutesByStop(@NonNull String stopID) {
        String[] uselessArray = {stopID};
        int count;
        Cursor result;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.query("routemap", QUERY_COLUMN_route, "stop = ?", uselessArray, null, null, null);
        } catch(SQLiteException e) {
            return null;
        }

        count = result.getCount();
        if(count == 0) {
            return null;
        }

        List<String> routes = new ArrayList<>(count);

        while(result.moveToNext()) {
            routes.add(result.getString(0));
        }

        result.close();

        return routes;
    }

    public String getNameFromID(@NonNull String stopID) {
        String[] uselessArray = {stopID};
        int count;
        String name;
        Cursor result;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.query(QUERY_TABLE_stops, QUERY_COLUMN_name, QUERY_WHERE_ID, uselessArray, null, null, null);
        } catch(SQLiteException e) {
            return null;
        }

        count = result.getCount();
        if(count == 0) {
            return null;
        }

        result.moveToNext();
        name = result.getString(0);

        result.close();

        return name;
    }

    public String getLocationFromID(@NonNull String stopID) {
        String[] uselessArray = {stopID};
        int count;
        String name;
        Cursor result;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.query(QUERY_TABLE_stops, QUERY_COLUMN_location, QUERY_WHERE_ID, uselessArray, null, null, null);
        } catch(SQLiteException e) {
            return null;
        }

        count = result.getCount();
        if(count == 0) {
            return null;
        }

        result.moveToNext();
        name = result.getString(0);

        result.close();

        return name;
    }

    public Stop getAllFromID(@NonNull String stopID) {
        Cursor result;
        int count;
        Stop s;

        if(this.db == null) {
            return null;
        }

        try {
            result = this.db.query(QUERY_TABLE_stops, QUERY_COLUMN_everything, QUERY_WHERE_ID, new String[] {stopID}, null, null, null);
            int colName = result.getColumnIndex("name");
            int colLocation = result.getColumnIndex("location");
            int colType = result.getColumnIndex("type");
            int colLat = result.getColumnIndex("lat");
            int colLon = result.getColumnIndex("lon");

            count = result.getCount();
            if(count == 0) {
                return null;
            }

            result.moveToNext();

            Route.Type type = routeTypeFromSymbol(result.getString(colType));

            String locationWhichSometimesIsAnEmptyString = result.getString(colLocation);
            if(locationWhichSometimesIsAnEmptyString.length() <= 0) {
                locationWhichSometimesIsAnEmptyString = null;
            }

            s = new Stop(stopID, result.getString(colName), null, locationWhichSometimesIsAnEmptyString, type, getRoutesByStop(stopID), result.getDouble(colLat), result.getDouble(colLon));
        } catch(SQLiteException e) {
            return null;
        }

        result.close();

        return s;
    }

    /**
     * Query some bus stops inside a map view
     *
     * You can obtain the coordinates from OSMDroid using something like this:
     *  BoundingBoxE6 bb = mMapView.getBoundingBox();
     *  double latFrom = bb.getLatSouthE6() / 1E6;
     *  double latTo = bb.getLatNorthE6() / 1E6;
     *  double lngFrom = bb.getLonWestE6() / 1E6;
     *  double lngTo = bb.getLonEastE6() / 1E6;
     */
    public Stop[] queryAllInsideMapView(double minLat, double maxLat, double minLng, double maxLng) {
        Stop[] stops = new Stop[0];

        Cursor result;
        int count;

        // coordinates must be strings in the where condition
        String minLatRaw = String.valueOf(minLat);
        String maxLatRaw = String.valueOf(maxLat);
        String minLngRaw = String.valueOf(minLng);
        String maxLngRaw = String.valueOf(maxLng);

        String stopID;
        Route.Type type;

        if(this.db == null) {
            return stops;
        }

        try {
            result = this.db.query(QUERY_TABLE_stops, QUERY_COLUMN_everything_and_ID, QUERY_WHERE_LAT_AND_LNG_IN_RANGE, new String[] {minLatRaw, maxLatRaw, minLngRaw, maxLngRaw}, null, null, null);

            int colID = result.getColumnIndex("ID");
            int colName = result.getColumnIndex("name");
            int colLocation = result.getColumnIndex("location");
            int colType = result.getColumnIndex("type");
            int colLat = result.getColumnIndex("lat");
            int colLon = result.getColumnIndex("lon");

            count = result.getCount();
            stops = new Stop[count];

            int i = 0;
            while(result.moveToNext()) {

                stopID = result.getString(colID);
                type = routeTypeFromSymbol(result.getString(colType));

                String locationWhichSometimesIsAnEmptyString = result.getString(colLocation);
                if (locationWhichSometimesIsAnEmptyString.length() <= 0) {
                    locationWhichSometimesIsAnEmptyString = null;
                }

                stops[i++] = new Stop(stopID, result.getString(colName), null,
                        locationWhichSometimesIsAnEmptyString, type, getRoutesByStop(stopID),
                        result.getDouble(colLat), result.getDouble(colLon));
            }

        } catch(SQLiteException e) {
            // TODO: put a warning in the log
            return stops;
        }

        result.close();

        return stops;
    }

    /**
     * Get a Route Type from its char symbol
     *
     * @param route The route symbol (e.g. "B")
     * @return The related Route.Type (e.g. Route.Type.Bus)
     */
    public static Route.Type routeTypeFromSymbol(String route) {
        switch (route) {
            case "M":
                return Route.Type.METRO;
            case "T":
                return Route.Type.RAILWAY;
        }

        // default with case "B"
        return Route.Type.BUS;
    }
}
