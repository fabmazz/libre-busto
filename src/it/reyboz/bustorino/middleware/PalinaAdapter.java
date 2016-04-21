/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;

/**
 * This once was a ListView Adapter for BusLine[].
 *
 * Thanks to Framentos developers for the guide:
 * http://www.framentos.com/en/android-tutorial/2012/07/16/listview-in-android-using-custom-listadapter-and-viewcache/#
 *
 * @author Valerio Bozzolan
 * @author Ludovico Pavesi
 */
public class PalinaAdapter extends ArrayAdapter<Route> {
    private LayoutInflater li;
    private static int row_layout = R.layout.entry_bus_line_passage;

    // hey look, a pattern!
    static class ViewHolder {
        TextView busLineIconTextView;
        TextView busLineVehicleIcon;
        TextView busLinePassagesTextView;
    }

    public PalinaAdapter(Context context, Palina p) {
        // TODO: find a more efficient way if there's one
        super(context, row_layout, p.queryAllRoutes());
        li = LayoutInflater.from(context);
    }

    /**
     * Some parts taken from the AdapterBusLines class.<br>
     * Some parts inspired by these enlightening tutorials:<br>
     * http://www.simplesoft.it/android/guida-agli-adapter-e-le-listview-in-android.html<br>
     * https://www.codeofaninja.com/2013/09/android-viewholder-pattern-example.html<br>
     * And some other bits and bobs TIRATI FUORI DAL NULLA CON L'INTUIZIONE INTELLETTUALE PERCHÉ
     * SEMBRA CHE NESSUNO ABBIA LA MINIMA IDEA DI COME FUNZIONA UN ADAPTER SU ANDROID.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;

        // TODO: determine why this is called twice with null and twice with a convertView, to display 2 rows.
        if(convertView == null) {
            // INFLATE!
            // setting a parent here is not supported and causes a fatal exception, apparently.
            convertView = li.inflate(row_layout, null);

            // STORE TEXTVIEWS!
            vh = new ViewHolder();
            vh.busLineIconTextView = (TextView) convertView.findViewById(R.id.busLineIcon);
            vh.busLineVehicleIcon = (TextView) convertView.findViewById(R.id.vehicleIcon);
            vh.busLinePassagesTextView = (TextView) convertView.findViewById(R.id.busLineNames);

            // STORE VIEWHOLDER IN\ON\OVER\UNDER\ABOVE\BESIDES THE VIEW!
            convertView.setTag(vh);
        } else {
            // RECOVER THIS STUFF!
            vh = (ViewHolder) convertView.getTag();
        }

        Route route = getItem(position);

        // Take the TextView from layout and set the busLine name
        // TODO: pezza temporanea da sistemare
        vh.busLineIconTextView.setText(route.name + " > " + route.destinazione);

        List<Passaggio> passaggi = route.passaggi;
        if(passaggi.size() == 0) {
            vh.busLinePassagesTextView.setText(R.string.no_passages);
            vh.busLineVehicleIcon.setVisibility(View.INVISIBLE);
        } else {
            String resultString = "";
            for(Passaggio passaggio : passaggi) {
                // "+" calls concat() and some other stuff internally, this should be faster
                resultString = resultString.concat(passaggio.toString()).concat(" ");
            }
            vh.busLinePassagesTextView.setText(resultString);
        }

        return convertView;
    }
}