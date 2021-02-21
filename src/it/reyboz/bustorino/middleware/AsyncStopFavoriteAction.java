/*
	BusTO  - Middleware components
    Copyright (C) 2016 Fabio Mazza

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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.widget.Toast;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.data.UserDB;

/**
 * Handler to add or remove or toggle a Stop in your favorites
 */
public class AsyncStopFavoriteAction extends AsyncTask<Stop, Void, Boolean> {
    private Context context;

    /**
     * Kind of actions available
     */
    public enum Action { ADD, REMOVE, TOGGLE };

    /**
     * Action chosen
     *
     * Note that TOGGLE is not converted to ADD or REMOVE.
     */
    private Action action;

    /**
     * Constructor
     *
     * @param context
     * @param action
     */
    public AsyncStopFavoriteAction(Context context, Action action) {
        this.context = context.getApplicationContext();
        this.action = action;
    }

    @Override
    protected Boolean doInBackground(Stop... stops) {
        boolean result = false;

        Stop stop = stops[0];

        // check if the request has sense
        if(stop != null) {

            // get a writable database
            UserDB userDatabase = new UserDB(context);
            SQLiteDatabase db = userDatabase.getWritableDatabase();

            // eventually toggle the status
            if(Action.TOGGLE.equals(action)) {
                if(UserDB.isStopInFavorites(db, stop.ID)) {
                    action = Action.REMOVE;
                } else {
                    action = Action.ADD;
                }
            }

            // at this point the action is just ADD or REMOVE

            // add or remove?
            if(Action.ADD.equals(action)) {
                // add
                result = UserDB.addOrUpdateStop(stop, db);
            } else {
                // remove
                result = UserDB.deleteStop(stop, db);
            }

            // please sir, close the door
            db.close();
        }

        return result;
    }

    /**
     * Callback fired when everything was done
     *
     * @param result
     */
    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if(result) {
            // at this point the action should be just ADD or REMOVE
            if(Action.ADD.equals(action)) {
                // now added
                Toast.makeText(this.context, R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
            } else {
                // now removed
                Toast.makeText(this.context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
            }
        } else {
            // wtf
            Toast.makeText(this.context, R.string.cant_add_to_favorites, Toast.LENGTH_SHORT).show();
        }
    }
}
