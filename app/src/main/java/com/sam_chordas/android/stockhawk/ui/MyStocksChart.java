package com.sam_chordas.android.stockhawk.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.db.chart.model.LineSet;
import com.db.chart.view.AxisController;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.model.StockHistory;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;

import java.util.ArrayList;
import java.util.List;

public class MyStocksChart extends AppCompatActivity {

    private String LOG_TAG = MyStocksActivity.class.getSimpleName();

    private List<StockHistory> stockHistoryRetrieved;
    private String selectedStock;
    private String selectedBidPrice;
    private String selectedPercentChange;
    private Intent mServiceIntent;

    LineChartView stockLineChart;
    TextView emptyTextView;
    TextView stockSymbolTextView;
    TextView stockBidPriceTextView;
    TextView stockPercentChangeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_line_graph);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(R.string.stock_detail);
        }

        stockLineChart = (LineChartView) findViewById(R.id.linechart);
        emptyTextView = (TextView) findViewById(R.id.chart_empty);

        stockSymbolTextView = (TextView) findViewById(R.id.gridlayout_symbol_value);
        stockBidPriceTextView = (TextView) findViewById(R.id.gridlayout_bid_price_value);
        stockPercentChangeTextView = (TextView) findViewById(R.id.gridlayout_percent_change_value);

        // Check internet connection and inform user if no connection
        if (Utils.checkConnection(this) == false) {
            stockLineChart.setVisibility(View.INVISIBLE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else if (Utils.checkConnection(this) == true){
            stockLineChart.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);

            // Get the symbol, price and percent change of selected stock
            selectedStock = getIntent().getStringExtra(MyStocksActivity.STOCK_SYMBOL);
            selectedBidPrice = getIntent().getStringExtra(MyStocksActivity.BID_PRICE);
            selectedPercentChange = getIntent().getStringExtra(MyStocksActivity.PERCENT_CHANGE);

            // Start service intent and passing symbol of selected stock
            mServiceIntent = new Intent(this, StockIntentService.class);
            mServiceIntent.putExtra("tag", "historical");
            mServiceIntent.putExtra("stock_symbol", selectedStock);
            startService(mServiceIntent);

            stockSymbolTextView.setText(selectedStock);
            stockBidPriceTextView.setText(selectedBidPrice);
            stockPercentChangeTextView.setText(selectedPercentChange);

        }

    }

    // Receive the broadcast and use data to plot chart
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            stockHistoryRetrieved = new ArrayList<StockHistory>();
            stockHistoryRetrieved = intent.getParcelableArrayListExtra("JSON");

            showLineChart();

        }
    };

    // Plot chart of selected stock using WilliamChart
    private void showLineChart(){
        LineSet stockDataset = new LineSet();
        stockDataset.setColor(Color.BLUE)
                .setSmooth(true)
                .setThickness(4);

        // Get the maximum and minimum values
        int minClose = 0;
        int maxClose = 0;
        for (int i = 0; i < stockHistoryRetrieved.size(); i++) {
            String stringDate = stockHistoryRetrieved.get(i).mDate;
            float floatClose = Float.parseFloat(stockHistoryRetrieved.get(i).mClose);

            if (floatClose > maxClose) {
                maxClose = (int) floatClose;
            }

            if (i == 0) {
                minClose = (int) floatClose;
            } else {
                if (floatClose < minClose) {
                    minClose = (int) floatClose;
                }
            }

            stockDataset.addPoint(stringDate, floatClose);
        }

        // setting up the scale for Y-axis
        int yRange = maxClose - minClose;
        double pad = yRange * 0.1;
        double scaleYMin = Math.floor(((minClose/pad) - 1) * pad);
        double scaleYMax = Math.ceil(((maxClose/pad) + 1) * pad);
        int YMin = (int) scaleYMin;
        int YMax = (int) scaleYMax;

        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1);

        stockLineChart.addData(stockDataset);

        stockLineChart.setBorderSpacing(10)
                .setGrid(ChartView.GridType.FULL, gridPaint)
                .setAxisColor(Color.BLACK)
                .setAxisBorderValues((int) (YMin) , (int) (YMax))
                .setLabelsColor(Color.BLACK)
                .setXLabels(AxisController.LabelPosition.NONE);

        stockLineChart.show();
    }

    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter("Results_From_JSON"));
    }

    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

}
