package com.sam_chordas.android.stockhawk.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.model.StockHistory;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.ui.MyStocksActivity;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService{
  private String LOG_TAG = StockTaskService.class.getSimpleName();
  public static final String ACTION_DATA_UPDATED = "com.sam_chordas.android.stockhawk.ACTION_DATA_UPDATED";

  private OkHttpClient client = new OkHttpClient();
  private Context mContext;
  private StringBuilder mStoredSymbols = new StringBuilder();
  private boolean isUpdate;
  private boolean isHistorical;

  public StockTaskService(){}

  public StockTaskService(Context context){
    mContext = context;
  }

  String fetchData(String url) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .build();

    Response response = client.newCall(request).execute();
    return response.body().string();
  }

  @Override
  public int onRunTask(TaskParams params){
    Cursor initQueryCursor;

    if (mContext == null){
      mContext = this;
    }

    // StringBuilder for non-historical data
    StringBuilder urlStringBuilder = new StringBuilder();
    // StringBuilder for historical chart data
    StringBuilder historicalUrlStringBuilder = new StringBuilder();

    try{
      // Base URL for the Yahoo query
      urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
      urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
        + "in (", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    if (params.getTag().equals("init") || params.getTag().equals("periodic")){
      isUpdate = true;
      isHistorical = false;
      initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
          new String[] { "Distinct " + QuoteColumns.SYMBOL }, null,
          null, null);

      if (initQueryCursor.getCount() == 0 || initQueryCursor == null){
        // Init task. Populates DB with quotes for the symbols seen below

        try {
          urlStringBuilder.append(
              URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }

      } else if (initQueryCursor != null){
        DatabaseUtils.dumpCursor(initQueryCursor);
        initQueryCursor.moveToFirst();

        for (int i = 0; i < initQueryCursor.getCount(); i++){
          mStoredSymbols.append("\""+
              initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"))+"\",");
          initQueryCursor.moveToNext();
        }
        mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");

        try {
          urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }

      }

      urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
              + "org%2Falltableswithkeys&callback=");

    } else if (params.getTag().equals("add")){
      isUpdate = false;
      isHistorical = false;
      // get symbol from params.getExtra and build query
      String stockInput = params.getExtras().getString("symbol");

      try {
        urlStringBuilder.append(URLEncoder.encode("\""+stockInput+"\")", "UTF-8"));
      } catch (UnsupportedEncodingException e){
        e.printStackTrace();
      }

      urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
              + "org%2Falltableswithkeys&callback=");

    } else if (params.getTag().equals("historical")) {
      isUpdate = false;
      isHistorical = true;

      // get current date
      Calendar calendar = Calendar.getInstance();
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
      String endDate = simpleDateFormat.format(calendar.getTime());

      // get starting date (set to 1 month before current)
      Calendar historicalDate = Calendar.getInstance();
      historicalDate.add(Calendar.MONTH, -1);
      String startDate = simpleDateFormat.format(historicalDate.getTime());

      // get symbol from params.getExtra and build query
      String selectedStock = params.getExtras().getString("chart_symbol");

      historicalUrlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");

      try {
        historicalUrlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol = ", "UTF-8"));
        historicalUrlStringBuilder.append(URLEncoder.encode("\""+selectedStock+"\"", "UTF-8"));
        historicalUrlStringBuilder.append(URLEncoder.encode(" and startDate = ", "UTF-8"));
        historicalUrlStringBuilder.append(URLEncoder.encode("\""+startDate+"\"", "UTF-8"));
        historicalUrlStringBuilder.append(URLEncoder.encode(" and endDate = ", "UTF-8"));
        historicalUrlStringBuilder.append(URLEncoder.encode("\""+endDate+"\"", "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }

      historicalUrlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
              + "org%2Falltableswithkeys&callback=");
    }

    String urlString;
    String getResponse;
    int result = GcmNetworkManager.RESULT_FAILURE;

    // Check if getting chart data from isHistorical
    // isHistorical == true means get chart data
    if (!isHistorical) {
      if (urlStringBuilder != null) {
        urlString = urlStringBuilder.toString();

        try {
          getResponse = fetchData(urlString);
          result = GcmNetworkManager.RESULT_SUCCESS;

          try {
            ContentValues contentValues = new ContentValues();
            // update ISCURRENT to 0 (false) so new data is current
            if (isUpdate){
              contentValues.put(QuoteColumns.ISCURRENT, 0);
              mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                      null, null);
            }

            if ((Utils.quoteJsonToContentVals(getResponse)).size() > 0) {

              mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                      Utils.quoteJsonToContentVals(getResponse));

            } else {

              result = GcmNetworkManager.RESULT_FAILURE;

            }

          } catch (RemoteException | OperationApplicationException e){
            Log.e(LOG_TAG, "Error applying batch insert", e);
          }

        } catch (IOException e) {
          e.printStackTrace();
        }

      }
    } else if (isHistorical) {

      urlString = historicalUrlStringBuilder.toString();

      try {
        getResponse = fetchData(urlString);
        result = GcmNetworkManager.RESULT_SUCCESS;

        try {
          List<StockHistory> resultsFromJSON = new ArrayList<StockHistory>();
          resultsFromJSON = getHistoricalDataFromJson(getResponse);

          Intent intent = new Intent("Results_From_JSON");
          intent.putParcelableArrayListExtra("JSON", (ArrayList<? extends Parcelable>) resultsFromJSON);
          LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        } catch (JSONException e) {
          e.printStackTrace();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

    }

    return result;
  }

  // Update Widget
  private void updateWidget() {
    Intent updateIntent = new Intent(ACTION_DATA_UPDATED).setPackage(mContext.getPackageName());
    mContext.sendBroadcast(updateIntent);
  }

  // Extract data from JSON string
  private List<StockHistory> getHistoricalDataFromJson(String stockJsonStr) throws JSONException {
    final String QUERY = "query";
    final String RESULTS = "results";
    final String QUOTE = "quote";
    final String SYMBOL = "Symbol";
    final String DATE = "Date";
    final String CLOSE = "Close";

    JSONObject stockJSON = new JSONObject(stockJsonStr);
    stockJSON = stockJSON.getJSONObject(QUERY);
    JSONArray stockArray = stockJSON.getJSONObject(RESULTS).getJSONArray(QUOTE);

    int stockDataSize = Integer.parseInt(stockJSON.getString("count"));
    List<StockHistory> stockResult = new ArrayList<StockHistory>();

    for (int i = 0; i < stockDataSize; i++) {
      String symbol;
      String date;
      String close;

      JSONObject stockObject = stockArray.getJSONObject(i);
      symbol = stockObject.getString(SYMBOL);
      date = stockObject.getString(DATE);
      close = stockObject.getString(CLOSE);
      stockResult.add(new StockHistory(symbol, date, close));
    }

    return stockResult;
  }

}
