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
package it.reyboz.bustorino;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.melnykov.fab.FloatingActionButton;

import java.io.UnsupportedEncodingException;

import it.reyboz.bustorino.lab.GTTSiteSucker.BusLine;
import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.MyDB;
import it.reyboz.bustorino.lab.NetworkTools;
import it.reyboz.bustorino.lab.adapters.AdapterBusLines;
import it.reyboz.bustorino.lab.adapters.AdapterBusStops;
import it.reyboz.bustorino.lab.asyncwget.AsyncWgetBusStopFromBusStopID;
import it.reyboz.bustorino.lab.asyncwget.AsyncWgetBusStopSuggestions;

public class ActivityMain extends ActionBarActivity {

    /*
     * Layout elements
     */
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private TextView busStopNameTextView;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ListView resultsListView;
    private FloatingActionButton floatingActionButton;

    /*
     * @see swipeRefreshLayout
     */
    private Handler handler = new Handler();
    private final Runnable refreshing = new Runnable() {
        public void run() {
            showSpinner(DOUBLE_SPINNER);
            asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStopID);
        }
    };

    /*
     * To toggle alphabetical
     */
    private final boolean SEARCH_BY_ID = true;
    private final boolean SEARCH_BY_NAME = false;
    private boolean searchMode;

    /*
     * Options
     */
    private final String OPTION_SHOW_LEGEND = "show_legend";

    /**
     * Last successfully searched bus stop ID
     */
    private String lastSuccessfullySearchedBusStopID = null;

    AsyncWgetBusStopSuggestions asyncWgetBusStopSuggestions;
    AsyncWgetBusStopFromBusStopID asyncWgetBusStopFromBusStopID;

    /**
     * Last successfully searched bus stop / bus stops
     */
    private BusStop[] busStopsCache;

    /*
     * SQLite
     */
    private MyDB mDbHelper;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        busStopSearchByIDEditText = (EditText) findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = (EditText) findViewById(R.id.busStopSearchByNameEditText);
        busStopNameTextView = (TextView) findViewById(R.id.busStopNameTextView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        howDoesItWorkTextView = (TextView) findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = (Button) findViewById(R.id.hideHintButton);
        resultsListView = (ListView) findViewById(R.id.resultsListView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);
        floatingActionButton.attachToListView(resultsListView);

        busStopSearchByIDEditText.setSelectAllOnFocus(true);
        busStopSearchByIDEditText
                .setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        // IME_ACTION_SEARCH alphabetical option
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            onSearchClick(v);
                            return true;
                        }
                        return false;
                    }
                });

        busStopSearchByNameEditText.setSelectAllOnFocus(true);
        busStopSearchByNameEditText
                .setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        // IME_ACTION_SEARCH alphabetical option
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                            onSearchClick(v);
                            return true;
                        }
                        return false;
                    }
                });

        // Get database in write mode
        mDbHelper = new MyDB(this);
        db = mDbHelper.getWritableDatabase();

        // Called when the layout is pulled down
        swipeRefreshLayout
                .setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        handler.post(refreshing);
                    }
                });

        /**
         * Deprecated! D:
         *
         * @author Marco Gagino!!!
         * @deprecated
         * @see https://developer.android.com/reference/android/support/v4/widget/SwipeRefreshLayout.html#setColorSchemeResources%28int...%29
         */
        swipeRefreshLayout.setColorScheme(R.color.red_500, R.color.green_500);

        setSearchModeBusStopID();

        // Intercept calls from URL intent
        boolean tryedFromIntent = false;
        String busStopID = null;
        Uri data = getIntent().getData();
        if (data != null) {
            busStopID = getBusStopIDFromUri(data);
            tryedFromIntent = true;
        }

        // ...or intercept calls from other activities
        if (!tryedFromIntent) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                busStopID = b.getString("bus-stop-ID");
                tryedFromIntent = true;
            }
        }

        if (busStopID == null) {
            // Show keyboard if can't start from intent
            showKeyboard();

            if (tryedFromIntent) {
                // Show a warning if you come from intent but can't start
                asyncWgetBusStopFromBusStopID(null);
            }
        } else {
            // Start from intent successfully
            busStopSearchByIDEditText.setText(busStopID);
            showSpinner();
            asyncWgetBusStopFromBusStopID(busStopID);
        }
    }


    /**
     * Reload bus stop timetable when it's fulled resumed.
     *
     * @Override
     */
    protected void onPostResume() {
        super.onPostResume();
        Log.d("ActivityMain", "onPostResume fired");
        if (searchMode == SEARCH_BY_ID && lastSuccessfullySearchedBusStopID != null && lastSuccessfullySearchedBusStopID.length() != 0) {
            showSpinner();
            busStopSearchByIDEditText.setText(lastSuccessfullySearchedBusStopID);
            asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStopID);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        actionHelpMenuItem = menu.findItem(R.id.action_help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                // Respond to the action bar's Up/Home button
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_help:
                showHints();
                return true;
            case R.id.action_favorites:
                startActivity(new Intent(ActivityMain.this, ActivityFavorites.class));
                return true;
            case R.id.action_about:
                startActivity(new Intent(ActivityMain.this, ActivityAbout.class));
                return true;
            case R.id.action_news:
                openIceweasel("http://blog.reyboz.it/tag/busto/");
                return true;
            case R.id.action_bugs:
                openIceweasel("https://bugs.launchpad.net/bus-torino");
                return true;
            case R.id.action_source:
                openIceweasel("https://code.launchpad.net/bus-torino");
                return true;
            case R.id.action_licence:
                openIceweasel("http://www.gnu.org/licenses/gpl-3.0.html");
                return true;
            case R.id.action_author:
                openIceweasel("http://boz.reyboz.it?lovebusto");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * It's pure magic <3
     *
     * @param query Part of the busStopName
     */
    private void asyncWgetBusStopSuggestions(String query) {
        if (query == null || query.length() == 0) {
            Toast.makeText(getApplicationContext(),
                    R.string.insert_bus_stop_name_error, Toast.LENGTH_SHORT).show();
            hideSpinner();
            return;
        }
        if (!NetworkTools.isConnected(getApplicationContext())) {
            NetworkTools.showNetworkError(getApplicationContext());
            hideSpinner();
            return;
        }
        if (asyncWgetBusStopSuggestions != null) {
            asyncWgetBusStopSuggestions.cancel(true);
            asyncWgetBusStopSuggestions = null;
        }
        try {
            asyncWgetBusStopSuggestions = new AsyncWgetBusStopSuggestions(query) {
                public void onReceivedBusStopNames(BusStop[] busStops, int status) {
                    hideSpinner();

                    switch (status) {
                        case AsyncWgetBusStopSuggestions.ERROR_EMPTY_DOM:
                        case AsyncWgetBusStopSuggestions.ERROR_DOM:
                            Toast.makeText(getApplicationContext(),
                                    R.string.parsing_error, Toast.LENGTH_SHORT).show();
                            break;

                        case AsyncWgetBusStopSuggestions.ERROR_NONE:
                            if (busStops.length == 0) {
                                Toast.makeText(getApplicationContext(),
                                        R.string.no_bus_stop_have_this_name, Toast.LENGTH_SHORT).show();
                            } else {
                                for (BusStop busStop : busStops) {
                                    MyDB.DBBusStop.addBusStop(db, busStop);
                                }
                                populateBusStopsLayout(busStops);
                            }
                            break;
                    }
                }
            };
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(getApplicationContext(),
                    R.string.encoding_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * It's pure magic <3
     *
     * @param busStopID
     */
    private void asyncWgetBusStopFromBusStopID(String busStopID) {
        if (busStopID == null || busStopID.length() == 0) {
            Toast.makeText(getApplicationContext(),
                    R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            hideSpinner();
            return;
        }
        if (!NetworkTools.isConnected(getApplicationContext())) {
            NetworkTools.showNetworkError(getApplicationContext());
            hideSpinner();
            return;
        }
        if (asyncWgetBusStopFromBusStopID != null) {
            asyncWgetBusStopFromBusStopID.cancel(true);
            asyncWgetBusStopFromBusStopID = null;
        }
        asyncWgetBusStopFromBusStopID = new AsyncWgetBusStopFromBusStopID(busStopID) {
            public void onReceivedBusStop(BusStop busStop, int status) {
                hideSpinner();

                switch (status) {
                    case AsyncWgetBusStopSuggestions.ERROR_EMPTY_DOM:
                    case AsyncWgetBusStopSuggestions.ERROR_DOM:
                        Toast.makeText(getApplicationContext(),
                                R.string.parsing_error, Toast.LENGTH_SHORT).show();
                        break;
                    case AsyncWgetBusStopFromBusStopID.ERROR_NO_PASSAGES_OR_NO_BUS_STOP:
                        Toast.makeText(getApplicationContext(),
                                R.string.no_passages, Toast.LENGTH_SHORT).show();
                        break;

                    case AsyncWgetBusStopSuggestions.ERROR_NONE:
                        lastSuccessfullySearchedBusStopID = busStop.getBusStopID();
                        MyDB.DBBusStop.addBusStop(db, busStop);
                        populateBusLinesLayout(busStop);
                        break;
                }
            }
        };
    }

    private void populateBusLinesLayout(BusStop busStop) {
        busStop.orderBusLinesByFirstArrival();

        // Remember last successfully searched busStopID
        String busStopID = busStop.getBusStopID();

        // Retrieve passages
        BusLine[] busLines = busStop.getBusLines();

        // No passages?
        if (busLines.length == 0) {
            Toast.makeText(getApplicationContext(),
                    R.string.no_arrival_times, Toast.LENGTH_SHORT).show();
            return;
        }

        BusStop dbBusStop = MyDB.DBBusStop.getBusStop(db, busStop.getBusStopID());

        String busStopNameDisplay;
        if (dbBusStop != null && dbBusStop.getBusStopUsername() != null) {
            busStopNameDisplay = dbBusStop.getBusStopUsername();
        } else if (busStop.getBusStopName() != null) {
            busStopNameDisplay = busStop.getBusStopName();
        } else {
            busStopNameDisplay = String.valueOf(busStopID);
        }
        busStopNameTextView.setText(String.format(
                getString(R.string.passages), busStopNameDisplay));

        // Hide the alphabetical before showing passages
        hideKeyboard();

        // Shows hints
        if (getOption(OPTION_SHOW_LEGEND, true)) {
            showHints();
        } else {
            hideHints();
        }

        prepareGUIForBusLines();

        resultsListView.setAdapter(new AdapterBusLines(this, R.layout.entry_bus_line_passage, busLines));
        resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                /**
                 * Casting because of Javamerda
                 * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                 */
                BusLine busLine = (BusLine) parent.getItemAtPosition(position);

                Log.i("ActivityMain", "Tapped busLine " + busLine.toString());
            }
        });
    }

    private void populateBusStopsLayout(BusStop[] busStops) {
        busStopNameTextView.setVisibility(View.GONE);

        hideKeyboard();

        prepareGUIForBusStops();

        resultsListView.setAdapter(new AdapterBusStops(this, R.layout.entry_bus_stop, busStops));
        resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                /**
                 * Casting because of Javamerda
                 * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                 */
                BusStop busStop = (BusStop) parent.getItemAtPosition(position);

                Intent intent = new Intent(ActivityMain.this,
                        ActivityMain.class);

                Bundle b = new Bundle();
                b.putString("bus-stop-ID", busStop.getBusStopID());
                intent.putExtras(b);
                startActivity(intent);

            }
        });
    }

    /**
     * OK this is pure shit
     *
     * @param v View clicked
     */
    public void onSearchClick(View v) {
        showSpinner();
        if (searchMode == SEARCH_BY_ID) {
            String busStopID = busStopSearchByIDEditText.getText().toString();
            asyncWgetBusStopFromBusStopID(busStopID);
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            asyncWgetBusStopSuggestions(query);
        }
    }

    /**
     * QR scan button clocked
     *
     * @param v View QRButton clicked
     */
    public void onQRButtonClick(View v) {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.initiateScan();
    }

    /**
     * Receive the Barcode Scanner Intent
     *
     * @param requestCode
     * @param resultCode
     * @param intent
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        Uri uri;
        try {
            uri = Uri.parse( scanResult.getContents() );
        } catch(NullPointerException e) {
            Toast.makeText(getApplicationContext(),
                    R.string.no_qrcode, Toast.LENGTH_SHORT).show();
            return;
        }

        String busStopID = getBusStopIDFromUri(uri);
        busStopSearchByIDEditText.setText(busStopID);
        showSpinner();
        asyncWgetBusStopFromBusStopID(busStopID);
    }

    public void onHideHint(View v) {
        hideHints();
        setOption(OPTION_SHOW_LEGEND, false);
    }

    public void onToggleKeyboardLayout(View v) {
        if (searchMode == SEARCH_BY_NAME) {
            setSearchModeBusStopID();
            if (busStopSearchByIDEditText.requestFocus()) {
                showKeyboard();
            }
        } else { // searchMode == SEARCH_BY_ID
            setSearchModeBusStopName();
            if (busStopSearchByNameEditText.requestFocus()) {
                showKeyboard();
            }
        }
    }

    public void addInFavorites(View v) {
        if (lastSuccessfullySearchedBusStopID != null) {
            BusStop busStop = new BusStop(lastSuccessfullySearchedBusStopID);
            busStop.setIsFavorite(true);
            MyDB.DBBusStop.addBusStop(db, busStop);

            BusStop dbBusStop = MyDB.DBBusStop.getBusStop(db, busStop.getBusStopID());
            if (dbBusStop == null || dbBusStop.getBusStopLocality() == null) {
                // This will also scrape the busStopLocality
                try {
                    new AsyncWgetBusStopSuggestions(busStop.getBusStopID()) {
                        @Override
                        public void onReceivedBusStopNames(BusStop[] busStops, int status) {
                            if (status == AsyncWgetBusStopSuggestions.ERROR_NONE) {
                                for (BusStop busStop : busStops) {
                                    MyDB.DBBusStop.addBusStop(db, busStop);
                                }
                            }
                        }
                    };
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            Toast.makeText(getApplicationContext(),
                    R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
        }
    }

    private void setOption(String optionName, boolean value) {
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putBoolean(optionName, value);
        editor.commit();
    }

    private boolean getOption(String optionName, boolean optDefault) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        return preferences.getBoolean(optionName, optDefault);
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = searchMode == SEARCH_BY_ID ? busStopSearchByIDEditText : busStopSearchByNameEditText;
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void setSearchModeBusStopID() {
        searchMode = SEARCH_BY_ID;
        busStopSearchByNameEditText.setVisibility(View.GONE);
        busStopSearchByNameEditText.setText("");
        busStopSearchByIDEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.alphabetical);
    }

    private void setSearchModeBusStopName() {
        searchMode = SEARCH_BY_NAME;
        busStopSearchByIDEditText.setVisibility(View.GONE);
        busStopSearchByIDEditText.setText("");
        busStopSearchByNameEditText.setVisibility(View.VISIBLE);
        floatingActionButton.setImageResource(R.drawable.numeric);
    }

    private void showHints() {
        howDoesItWorkTextView.setVisibility(View.VISIBLE);
        hideHintButton.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(false);
    }

    private void hideHints() {
        howDoesItWorkTextView.setVisibility(View.GONE);
        hideHintButton.setVisibility(View.GONE);
        actionHelpMenuItem.setVisible(true);
    }

    private final boolean DOUBLE_SPINNER = true;
    private final boolean NORMAL_SPINNER = false;

    private void showSpinner(boolean swipeSpinner) {
        if (swipeSpinner == DOUBLE_SPINNER) {
            swipeRefreshLayout.setRefreshing(true);
        } else { // NORMAL_SPINNER
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void showSpinner() {
        showSpinner(NORMAL_SPINNER);
    }

    private void hideSpinner() {
        swipeRefreshLayout.setRefreshing(false);
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void prepareGUIForBusLines() {
        busStopNameTextView.setVisibility(View.VISIBLE);
        busStopNameTextView.setClickable(true);
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        resultsListView.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        busStopNameTextView.setText(getString(R.string.results));
        busStopNameTextView.setClickable(false);
        busStopNameTextView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        resultsListView.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(false);
        actionHelpMenuItem.setVisible(false);
    }

    /**
     * Open an URL in the default browser.
     *
     * @param url URL
     */
    public void openIceweasel(String url) {
        Intent browserIntent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent1);
    }

    /**
     * Try to extract the bus stop ID from a URi
     *
     * @param uri The URL
     * @return bus stop ID or null
     */
    public static String getBusStopIDFromUri(Uri uri) {
        String busStopID;
        switch (uri.getHost()) {
            case "m.gtt.to.it":
                // http://m.gtt.to.it/m/it/arrivi.jsp?n=1254
                busStopID = uri.getQueryParameter("n");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?n from: " + uri);
                }
                break;
            case "www.gtt.to.it":
                // http://www.gtt.to.it/cms/percorari/arrivi?palina=1254cd
                busStopID = uri.getQueryParameter("palina");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?palina from: " + uri);
                }
            default:
                Log.e("ActivityMain", "Unexpected intent URL: " + uri);
                busStopID = null;
        }
        return busStopID;
    }
}