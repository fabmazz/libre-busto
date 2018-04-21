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
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
//import com.melnykov.fab.FloatingActionButton;
import android.support.design.widget.FloatingActionButton;

import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.FiveTScraperFetcher;
import it.reyboz.bustorino.backend.FiveTStopsFetcher;
import it.reyboz.bustorino.backend.GTTJSONFetcher;
import it.reyboz.bustorino.backend.GTTStopsFetcher;
import it.reyboz.bustorino.backend.StopsFinderByName;
import it.reyboz.bustorino.fragments.FragmentHelper;
import it.reyboz.bustorino.fragments.FragmentListener;
import it.reyboz.bustorino.fragments.NearbyStopsFragment;
import it.reyboz.bustorino.fragments.ResultListFragment;
import it.reyboz.bustorino.middleware.*;

public class ActivityMain extends GeneralActivity implements FragmentListener {

    /*
     * Layout elements
     */
    private EditText busStopSearchByIDEditText;
    private EditText busStopSearchByNameEditText;
    private ProgressBar progressBar;
    private TextView howDoesItWorkTextView;
    private Button hideHintButton;
    private MenuItem actionHelpMenuItem;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton floatingActionButton;
    private FragmentManager framan;

    /*
     * Search mode
     */
    private static final int SEARCH_BY_NAME = 0;
    private static final int SEARCH_BY_ID = 1;
    private static final int SEARCH_BY_ROUTE = 2; // TODO: implement this (bug #1512948)
    private int searchMode;

    /*
     * Options
     */
    private final String OPTION_SHOW_LEGEND = "show_legend";
    private final String LOCATION_PERMISSION_GIVEN = "loc_permission";

    /* // useful for testing:
    public class MockFetcher implements ArrivalsFetcher {
        @Override
        public Palina ReadArrivalTimesAll(String routeID, AtomicReference<result> res) {
            SystemClock.sleep(5000);
            res.set(result.SERVER_ERROR);
            return new Palina();
        }
    }
    private ArrivalsFetcher[] ArrivalFetchers = {new MockFetcher(), new MockFetcher(), new MockFetcher(), new MockFetcher(), new MockFetcher()};*/

    private RecursionHelper<ArrivalsFetcher> ArrivalFetchersRecursionHelper = new RecursionHelper<>(new ArrivalsFetcher[] {new GTTJSONFetcher(), new FiveTScraperFetcher()});
    private RecursionHelper<StopsFinderByName> StopsFindersByNameRecursionHelper = new RecursionHelper<>(new StopsFinderByName[] {new GTTStopsFetcher(), new FiveTStopsFetcher()});

    private StopsDB stopsDB;
    private UserDB userDB;
    private FragmentHelper fh;

    ///////////////////////////////// EVENT HANDLERS ///////////////////////////////////////////////

    /*
     * @see swipeRefreshLayout
     */
    private Handler handler = new Handler();
    private final Runnable refreshing = new Runnable() {
        public void run() {
                new AsyncDataDownload(AsyncDataDownload.RequestType.ARRIVALS,fh).execute();
        }
    };

    //// MAIN METHOD ///

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        framan = getSupportFragmentManager();
        this.stopsDB = new StopsDB(getApplicationContext());
        this.userDB = new UserDB(getApplicationContext());
        setContentView(R.layout.activity_main);
        busStopSearchByIDEditText = (EditText) findViewById(R.id.busStopSearchByIDEditText);
        busStopSearchByNameEditText = (EditText) findViewById(R.id.busStopSearchByNameEditText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        howDoesItWorkTextView = (TextView) findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = (Button) findViewById(R.id.hideHintButton);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.listRefreshLayout);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);

        framan.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                Log.d("MainActivity, BusTO", "BACK STACK CHANGED");
            }
        });

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

        // Called when the layout is pulled down
        swipeRefreshLayout
                .setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        handler.post(refreshing);
                    }
                });

        /**
         * @author Marco Gagino!!!
         */
        //swipeRefreshLayout.setColorSchemeColors(R.color.blue_500, R.color.orange_500); // setColorScheme is deprecated, setColorSchemeColors isn't
        swipeRefreshLayout.setColorSchemeResources(R.color.blue_500,R.color.orange_500);
        fh = new FragmentHelper(this,R.id.listRefreshLayout,R.id.resultFrame,FragmentHelper.NO_FRAME);
        setSearchModeBusStopID();

        //---------------------------- START INTENT CHECK QUEUE ------------------------------------

        // Intercept calls from URL intent
        boolean tryedFromIntent = false;

        String busStopID = null;
        String busStopDisplayName = null;
        Uri data = getIntent().getData();
        if (data != null) {
            busStopID = getBusStopIDFromUri(data);
            tryedFromIntent = true;
        }

        // Intercept calls from other activities
        if (!tryedFromIntent) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                busStopID = b.getString("bus-stop-ID");
                busStopDisplayName = b.getString("bus-stop-display-name");

                /**
                 * I'm not very sure if you are coming from an Intent.
                 * Some launchers work in strange ways.
                 */
                tryedFromIntent = busStopID != null;
            }
        }

        //---------------------------- END INTENT CHECK QUEUE --------------------------------------

        if (busStopID == null) {
            // Show keyboard if can't start from intent
            showKeyboard();

            // You haven't obtained anything... from an intent?
            if (tryedFromIntent) {

                // This shows a luser warning
                ArrivalFetchersRecursionHelper.reset();
                Toast.makeText(getApplicationContext(),
                        R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            // If you are here an intent has worked successfully
            setBusStopSearchByIDEditText(busStopID);
            /*
            //THIS PART SHOULDN'T BE NECESSARY SINCE THE LAST SUCCESSFULLY SEARCHED BUS
            // STOP IS ADDED AUTOMATICALLY
            Stop nextStop = new Stop(busStopID);
            // forcing it as user name even though it could be standard name, it doesn't really matter
            nextStop.setStopUserName(busStopDisplayName);
            //set stop as last succe
            fh.setLastSuccessfullySearchedBusStop(nextStop);
            */
            createFragmentForStop(busStopID);
        }
        //Try (hopefully) database update
        //TODO: Start the service in foreground, check last time it ran before
        DatabaseUpdateService.startDBUpdate(getApplicationContext());

        //locationHandler = new GPSLocationAdapter(getApplicationContext());
        //--------- NEARBY STOPS--------//
        //Let's search stops nearby
        LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(locManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && fh.getLastSuccessfullySearchedBusStop()==null && getOption(LOCATION_PERMISSION_GIVEN,false)) {
            NearbyStopsFragment fr = NearbyStopsFragment.newInstance();
            FragmentTransaction ft = framan.beginTransaction();
            ft.add(R.id.resultFrame, fr, "Location");
            ft.commitNow();
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setEnabled(false);
        } else if(!getOption(LOCATION_PERMISSION_GIVEN, false)){
            assertLocationPermissions();
        }
        Log.d("MainActivity", "Created");
    }

    /**
     * Reload bus stop timetable when it's fulled resumed from background.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        Log.d("ActivityMain", "onPostResume fired. Last successfully bus stop ID: " + fh.getLastSuccessfullySearchedBusStop());
        if (searchMode == SEARCH_BY_ID && fh.getLastSuccessfullySearchedBusStop() != null) {
            setBusStopSearchByIDEditText(fh.getLastSuccessfullySearchedBusStop().ID);
            //new asyncWgetBusStopFromBusStopID(lastSuccessfullySearchedBusStop.ID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);
            new AsyncDataDownload(AsyncDataDownload.RequestType.ARRIVALS,fh).execute();
        } else {
            //we have new activity or we don't have a new searched stop.
            //Let's search stops nearby
            LocationManager locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.resultFrame);


        }
        //show the FAB since it remains hidden
        floatingActionButton.show();

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
     * OK this is pure shit
     *
     * @param v View clicked
     */
    public void onSearchClick(View v) {
        if (searchMode == SEARCH_BY_ID) {
            String busStopID = busStopSearchByIDEditText.getText().toString();
            //OLD ASYNCTASK
            //new asyncWgetBusStopFromBusStopID(busStopID, ArrivalFetchersRecursionHelper, lastSuccessfullySearchedBusStop);

            if(busStopID == null || busStopID.length() <= 0) {
                showMessage(R.string.insert_bus_stop_number_error);
                toggleSpinner(false);
            } else{
                new AsyncDataDownload(AsyncDataDownload.RequestType.ARRIVALS,fh).execute(busStopID);
                Log.d("MainActiv","Started search for arrivals of stop "+busStopID);
            }
        } else { // searchMode == SEARCH_BY_NAME
            String query = busStopSearchByNameEditText.getText().toString();
            //new asyncWgetBusStopSuggestions(query, stopsDB, StopsFindersByNameRecursionHelper);
            new AsyncDataDownload(AsyncDataDownload.RequestType.STOPS,fh).execute(query);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case PERMISSION_REQUEST_POSITION:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    setOption(LOCATION_PERMISSION_GIVEN,true);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("mainActivity","Recreating stop fragment");
                            NearbyStopsFragment fragment = NearbyStopsFragment.newInstance();
                            Fragment oldFrag = framan.findFragmentById(R.id.resultFrame);
                            FragmentTransaction ft = framan.beginTransaction();
                            if(oldFrag!=null)
                                ft.remove(oldFrag);
                            ft.add(R.id.resultFrame,fragment,"nearbyStop_correct");
                            ft.commit();
                            framan.executePendingTransactions();
                        }
                    });
                } else {
                    //permission denied
                    setOption(LOCATION_PERMISSION_GIVEN,false);
                }
            //add other cases for permissions
        }

    }

    @Override
    public void createFragmentForStop(String ID) {
        //new asyncWgetBusStopFromBusStopID(ID, ArrivalFetchersRecursionHelper,lastSuccessfullySearchedBusStop);
        if(ID == null || ID.length() <= 0) {
            // we're still in UI thread, no need to mess with Progress
            showMessage(R.string.insert_bus_stop_number_error);
            toggleSpinner(false);
        } else {
            new AsyncDataDownload(AsyncDataDownload.RequestType.ARRIVALS,fh).execute(ID);
            Log.d("MainActiv","Started search for arrivals of stop "+ID);
        }
    }



    /**
     * QR scan button clicked
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
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        Uri uri;
        try {
            uri = Uri.parse(scanResult != null ? scanResult.getContents() : null); // this apparently prevents NullPointerException. Somehow.
        } catch (NullPointerException e) {
            Toast.makeText(getApplicationContext(),
                    R.string.no_qrcode, Toast.LENGTH_SHORT).show();
            return;
        }

        String busStopID = getBusStopIDFromUri(uri);
        busStopSearchByIDEditText.setText(busStopID);
        createFragmentForStop(busStopID);
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




    ///////////////////////////////// OTHER STUFF //////////////////////////////////////////////////



    @Override
    public void addLastStopToFavorites() {
        if(fh.getLastSuccessfullySearchedBusStop() != null) {
            new AsyncAddToFavorites(this).execute(fh.getLastSuccessfullySearchedBusStop());
        }
    }

    @Override
    public void showFloatingActionButton(boolean yes) {
        if(yes) floatingActionButton.show();
        else floatingActionButton.hide();
    }

    @Override
    public void enableRefreshLayout(boolean yes) {
        swipeRefreshLayout.setEnabled(yes);
    }


    ////////////////////////////////////// GUI HELPERS /////////////////////////////////////////////
    @Override
    public void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = searchMode == SEARCH_BY_ID ? busStopSearchByIDEditText : busStopSearchByNameEditText;
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void showMessage(int messageID) {
        Toast.makeText(getApplicationContext(), messageID, Toast.LENGTH_SHORT).show();
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

    /**
     * Having that cursor at the left of the edit text makes me cancer.
     * @param busStopID bus stop ID
     */
    private void setBusStopSearchByIDEditText(String busStopID) {
        busStopSearchByIDEditText.setText(busStopID);
        busStopSearchByIDEditText.setSelection(busStopID.length());
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

    //TODO: toggle spinner from mainActivity
    @Override
    public void toggleSpinner(boolean enable) {
        if (enable) {
            //already set by the RefreshListener when needed
            //swipeRefreshLayout.setRefreshing(true);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            swipeRefreshLayout.setRefreshing(false);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void prepareGUIForBusLines() {
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(true);
    }

    private void prepareGUIForBusStops() {
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        actionHelpMenuItem.setVisible(false);
    }

    /**
     * This provides a temporary fix to make the transition
     * to a single asynctask go smoother
     * @param fragmentType the type of fragment created
     */
    @Override
    public void readyGUIfor(String fragmentType) {
        hideKeyboard();
        if(fragmentType==null) Log.e("ActivityMain","Problem with fragmentType");
        else
        switch (fragmentType){
            case ResultListFragment.TYPE_LINES:
                prepareGUIForBusLines();
                if (getOption(OPTION_SHOW_LEGEND, true)) {
                    showHints();
                }
                break;
            case ResultListFragment.TYPE_STOPS:
                prepareGUIForBusStops();
                break;
            default:
                Log.e("BusTO Activity","Called readyGUI with unsupported type of Fragment");
                return;
        }
        // Shows hints

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

    ///////////////////// INTENT HELPER ////////////////////////////////////////////////////////////

    /**
     * Try to extract the bus stop ID from a URi
     *
     * @param uri The URL
     * @return bus stop ID or null
     */
    public static String getBusStopIDFromUri(Uri uri) {
        String busStopID;

        // everithing catches fire when passing null to a switch.
        String host = uri.getHost();
        if(host == null) {
            Log.e("ActivityMain", "Not an URL: " + uri);
            return null;
        }

        switch(host) {
            case "m.gtt.to.it":
                // http://m.gtt.to.it/m/it/arrivi.jsp?n=1254
                busStopID = uri.getQueryParameter("n");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?n from: " + uri);
                }
                break;
            case "www.gtt.to.it":
            case "gtt.to.it":
                // http://www.gtt.to.it/cms/percorari/arrivi?palina=1254
                busStopID = uri.getQueryParameter("palina");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?palina from: " + uri);
                }
                break;
            default:
                Log.e("ActivityMain", "Unexpected intent URL: " + uri);
                busStopID = null;
        }
        return busStopID;
    }
}