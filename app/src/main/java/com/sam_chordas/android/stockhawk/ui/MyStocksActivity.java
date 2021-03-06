package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;
import com.sam_chordas.android.stockhawk.widget.StockWidgetProvider;

/**
 *  Main activity showing a list of stocks with bid prices.
 *  This file was originally created by Udacity.
 *  File was modified for the Nanodegree Program.
 */
public class MyStocksActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

  public static final String STOCK_SYMBOL = "stock_symbol";
  public static final String BID_PRICE = "bid_price";
  public static final String PERCENT_CHANGE = "percent_change";
  private static final String SERVICE_INTENT_TAG = "tag";
  private static final String SERVICE_INTENT_INIT = "init";
  private static final String SERVICE_INTENT_ADD = "add";
  private static final String SERVICE_INTENT_SYMBOL = "symbol";
  private static final String PERIODIC_TAG = "periodic";

  /**
   * Used to store the last screen title. For use in {@link #restoreActionBar()}.
   */
  private CharSequence mTitle;
  private Intent mServiceIntent;
  private ItemTouchHelper mItemTouchHelper;
  private static final int CURSOR_LOADER_ID = 0;
  private QuoteCursorAdapter mCursorAdapter;
  private Context mContext;
  private Cursor mCursor;
  boolean isConnected;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my_stocks);

    mContext = this;
    final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    TextView emptyTextView = (TextView) findViewById(R.id.recycler_view_empty);

    // Checking to see if there is any internet connection
    // Shows "reminder" screen if no connection detected
    if (Utils.checkConnection(mContext) == false) {

      isConnected = false;
      emptyTextView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
      networkToast();

      FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
      fab.setOnClickListener(new View.OnClickListener() {

        @Override
        public void onClick(View v) {
          networkToast();
        }
      });

    } else if (Utils.checkConnection(mContext) == true) {

      isConnected = true;

      emptyTextView.setVisibility(View.GONE);
      recyclerView.setVisibility(View.VISIBLE);

      mServiceIntent = new Intent(this, StockIntentService.class);
      if (savedInstanceState == null) {
        mServiceIntent.putExtra(SERVICE_INTENT_TAG, SERVICE_INTENT_INIT);
        startService(mServiceIntent);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
        mCursorAdapter = new QuoteCursorAdapter(this, null);

        recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
                new RecyclerViewItemClickListener.OnItemClickListener() {
                  @Override public void onItemClick(View v, int position) {
                    // Start new activity to display stock chart
                    TextView textViewSymbol = (TextView) v.findViewById(R.id.stock_symbol);
                    String strSymbol = textViewSymbol.getText().toString();
                    TextView textViewBidPrice = (TextView) v.findViewById(R.id.bid_price);
                    String strBidPrice = textViewBidPrice.getText().toString();
                    TextView textViewPercentChange = (TextView) v.findViewById(R.id.change);
                    String strPercentChange = textViewPercentChange.getText().toString();
                    Intent intent = new Intent(getApplicationContext(), MyStocksChart.class)
                            .putExtra(STOCK_SYMBOL, strSymbol)
                            .putExtra(BID_PRICE, strBidPrice)
                            .putExtra(PERCENT_CHANGE, strPercentChange);
                    startActivity(intent);
                  }
                }));

        recyclerView.setAdapter(mCursorAdapter);

      }

      // Floating Action Button for adding new stock
      FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
      fab.attachToRecyclerView(recyclerView);
      fab.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          if(isConnected) {
            new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                    .content(R.string.content_test)
                    .inputType(InputType.TYPE_CLASS_TEXT)
                    .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                      @Override
                      public void onInput(MaterialDialog dialog, CharSequence input) {
                        // On FAB click, receive user input. Make sure the stock doesn't already exist
                        // in the DB and proceed accordingly
                        Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                new String[]{QuoteColumns.SYMBOL}, QuoteColumns.SYMBOL + "= ?",

                                new String[]{input.toString().toUpperCase()}, null);
                        if (c.getCount() != 0) {
                          Toast toast =
                                  Toast.makeText(MyStocksActivity.this, getString(R.string.already_added),
                                          Toast.LENGTH_LONG);
                          toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                          toast.show();
                          return;
                        } else {
                          // Add the stock to DB
                          mServiceIntent.putExtra(SERVICE_INTENT_TAG, SERVICE_INTENT_ADD);
                          mServiceIntent.putExtra(SERVICE_INTENT_SYMBOL, input.toString().toUpperCase());
                          startService(mServiceIntent);
                        }
                      }
                    })
                    .show();
          } else if (!isConnected){
            networkToast();
          }

        }
      });

      ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
      mItemTouchHelper = new ItemTouchHelper(callback);
      mItemTouchHelper.attachToRecyclerView(recyclerView);

      mTitle = getTitle();
      if (isConnected){
        long period = 3600L;
        long flex = 10L;
        //String periodicTag = "periodic";

        // create a periodic task to pull stocks once every hour after the app has been opened. This
        // is so Widget data stays up to date.
        PeriodicTask periodicTask = new PeriodicTask.Builder()
                .setService(StockTaskService.class)
                .setPeriod(period)
                .setFlex(flex)
                .setTag(PERIODIC_TAG)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setRequiresCharging(false)
                .build();
        // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
        // are updated.
        GcmNetworkManager.getInstance(this).schedule(periodicTask);
      }

    }
  }

  @Override
  public void onRestart() {
    super.onRestart();
    getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
  }

  public void networkToast(){
    Toast.makeText(mContext, getString(R.string.network_toast), Toast.LENGTH_SHORT).show();
  }

  public void restoreActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      getMenuInflater().inflate(R.menu.my_stocks, menu);
      restoreActionBar();
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    if (id == R.id.action_change_units){
      // this is for changing stock changes from percent value to dollar value
      Utils.showPercent = !Utils.showPercent;
      this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
    }

    if (id == R.id.menu_refresh) {
      // refresh activity (e.g. when wifi has just been turned on)
      finish();
      startActivity(getIntent());
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args){
    // This narrows the return to only the stocks that are most current.
    return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
        new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
        QuoteColumns.ISCURRENT + " = ?",
        new String[]{"1"},
        null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data){
    if (isConnected) {
      mCursorAdapter.swapCursor(data);
      mCursor = data;
      updateWidget();
    } else {
      getLoaderManager().destroyLoader(CURSOR_LOADER_ID);
    }
  }

  // Updates the widget (e.g. when user deletes a stock)
  private void updateWidget() {
    ComponentName name = new ComponentName(this, StockWidgetProvider.class);
    int[] ids = AppWidgetManager.getInstance(this).getAppWidgetIds(name);

    Intent intent = new Intent(this, StockWidgetProvider.class);
    intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
    sendBroadcast(intent);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader){
    mCursorAdapter.swapCursor(null);
  }

}
