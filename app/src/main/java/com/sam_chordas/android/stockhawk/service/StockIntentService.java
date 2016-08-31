package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.model.StockHistory;

import java.util.List;

/**
 * Created by sam_chordas on 10/1/15.
 */
public class StockIntentService extends IntentService {

  public StockIntentService(){
    super(StockIntentService.class.getName());
  }

  public StockIntentService(String name) {
    super(name);
  }

  @Override protected void onHandleIntent(Intent intent) {

    StockTaskService stockTaskService = new StockTaskService(this);
    Bundle args = new Bundle();

    if (intent.getStringExtra("tag").equals("add")){
      args.putString("symbol", intent.getStringExtra("symbol"));
    } else if (intent.getStringExtra("tag").equals("historical")) {
      args.putString("chart_symbol", intent.getStringExtra("stock_symbol"));
    }

    // We can call OnRunTask from the intent service to force it to run immediately instead of
    // scheduling a task.
    try {
      stockTaskService.onRunTask(new TaskParams(intent.getStringExtra("tag"), args));
    } catch (NumberFormatException e) {
      // Inform user of invalid stock symbol added
      // Idea of using handler taken from StackOverflow.com
      // http://stackoverflow.com/questions/7378936/how-to-show-toast-message-from-background-thread
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(getApplicationContext(), R.string.invalid_symbol, Toast.LENGTH_LONG).show();
        }
      });
    }

  }
}
