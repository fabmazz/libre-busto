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

package it.reyboz.bustorino.middleware;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import it.reyboz.bustorino.backend.Stop;

public class UserDB extends SQLiteOpenHelper {
	public static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "user.db";
    private final Context c; // needed during upgrade
    private static String[] usernameColumnNameAsArray = {"username"};
    private static String[] getFavoritesColumnNamesAsArray = {"ID", "standardname", "username"};

	public UserDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.c = context;
	}

    @Override
	public void onCreate(SQLiteDatabase db) {
        // exception intentionally left unhandled
		db.execSQL("CREATE TABLE favorites (ID TEXT PRIMARY KEY NOT NULL, standardname TEXT, username TEXT)");

        if(OldDB.doesItExist(this.c)) {
            upgradeFromOldDatabase();
        }
	}

    private void upgradeFromOldDatabase() {
        OldDB old;
        try {
            old = new OldDB(this.c);
        } catch(IllegalStateException e) {
            // can't create database => it doesn't really exist, no matter what doesItExist() says
            return;
        }

        int ver = old.getOldVersion();

        /* version 8 was the previous version, OldDB "upgrades" itself to 1337 but unless the app
         * has crashed midway through the upgrade and the user is retrying, that should never show
         * up here. And if it does, try to recover favorites anyway.
         * Versions < 8 already got dropped during the update process, so let's do the same.
         */
        if(ver >= 8) {
            ArrayList<String> ID = new ArrayList<>();
            ArrayList<String> name = new ArrayList<>();
            ArrayList<String> username = new ArrayList<>();
            int len;
            int len2;

            try {
                Cursor c = old.getReadableDatabase().rawQuery("SELECT busstop_ID, busstop_name, busstop_username FROM busstop WHERE busstop_isfavorite = 1 ORDER BY busstop_name ASC", new String[] {});

                int zero = c.getColumnIndex("busstop_ID");
                int one = c.getColumnIndex("busstop_name");
                int two = c.getColumnIndex("busstop_username");

                while(c.moveToNext()) {
                    ID.add(c.getString(zero));

                    if(c.getString(one).length() <= 0) {
                        name.add(null);
                    } else {
                        name.add(c.getString(one));
                    }

                    if(c.getString(two).length() <= 0) {
                        username.add(null);
                    } else {
                        username.add(c.getString(two));
                    }
                }

                c.close();
                old.close();
            } catch(Exception ignored) {
                // there's no hope, go ahead and nuke old database.
            }

            len = ID.size();
            len2 = name.size();
            if(len2 < len) {
                len = len2;
            }
            len2 = username.size();
            if(len2 < len) {
                len = len2;
            }


            if (len > 0) {
                SQLiteDatabase newdb = this.getWritableDatabase();
                newdb.beginTransaction();

                try {
                    for (int i = 0; i < len; i++) {
                        newdb.rawQuery("INSERT INTO favorites(ID, standardname, username) VALUES (?, ?, ?)", new String[]{ID.get(i), name.get(i), username.get(i)});
                    }
                    newdb.setTransactionSuccessful();
                } finally {
                    newdb.endTransaction();
                }

                newdb.close();
            }
        }

        if(!OldDB.destroy(this.c)) {
            // TODO: notify user somehow?
            Log.e("UserDB", "Failed to delete old database, you should really uninstall and reinstall the app. Unfortunately I have no way to tell the user.");
        }
    }

    @Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do yet
	}

    @Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do yet
	}

    /**
     * Gets stop name set by the user.
     *
     * @param db readable database
     * @param stopID stop ID
     * @return name set by user, or null if not set\not found
     */
    public static String getStopUserName(SQLiteDatabase db, String stopID) {
        String username = null;

        try {
            Cursor c = db.query("favorites", usernameColumnNameAsArray, "ID = ?", new String[] {stopID}, null, null, null, null);

            if(c.moveToNext()) {
                username = c.getString(c.getColumnIndex("username"));
            }
            c.close();
        } catch(SQLiteException ignored) {}

        return username;
    }

    public static List<Stop> getFavorites(SQLiteDatabase db) {
        List<Stop> l = new ArrayList<>();

        try {
            Cursor c = db.query("favorites", getFavoritesColumnNamesAsArray, null, null, null, null, null, null);
            int colID = c.getColumnIndex("ID");
            int colStandard = c.getColumnIndex("standardname");
            int colUser = c.getColumnIndex("username");

            if(c.moveToNext()) {
                String name = c.getString(colUser);
                if(name == null || name.length() <= 0) {
                    l.add(new Stop(c.getString(colStandard), c.getString(colID), null, null, null));
                } else {
                    l.add(new Stop(name, c.getString(colID), null, null, null));
                }
            }

            c.close();
        } catch(SQLiteException ignored) {}

        return l;
    }

    public boolean addOrUpdate(Stop s, SQLiteDatabase db) {
        String username;

        username = getStopUserName(db, s.ID);

        try {
            if(username == null) {
                db.rawQuery("INSERT INTO favorites(ID, standardname, username) VALUES (?, ?, null)", new String[] {s.ID, s.getStopName()}).close();
            } else {
                db.rawQuery("INSERT INTO favorites(ID, standardname, username) VALUES (?, ?, ?)", new String[] {s.ID, s.getStopName(), username}).close();
            }
            // UPSERT.
            // standardname is the only field that could have changed...
            db.rawQuery("UPDATE favorites SET standardname = ? WHERE ID = ?", new String[]{s.getStopName(), s.ID}).close();
        } catch(SQLiteException e) {
            return false;
        }

        return true;
    }
}