package com.sam_chordas.android.stockhawk.widget;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.ui.MyStocksChart;

/**
 * Created by maile on 8/24/2016.
 */
public class StockWidgetRemoteViewsService extends RemoteViewsService {

    private String LOG_TAG = StockWidgetRemoteViewsService.class.getSimpleName();
    static final String STOCK_SYMBOL = "stock_symbol";
    static final String BID_PRICE = "bid_price";
    static final String PERCENT_CHANGE = "percent_change";

    private static final String[] STOCKS_COLUMN = {
            QuoteColumns.SYMBOL,
            QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE
    };

    private static final int INDEX_SYMBOL = 0;
    private static final int INDEX_BIDPRICE = 1;
    private static final int INDEX_PERCENT_CHANGE = 2;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            // Initialize data set
            @Override
            public void onCreate() {

            }

            @Override
            public void onDataSetChanged() {
                if (data != null) {
                    data.close();
                }

                // Clear and restore the calling identity so that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();

                // Get data from ContentProvider
                Uri stockListUri = QuoteProvider.Quotes.CONTENT_URI;
                data = getContentResolver().query(stockListUri, STOCKS_COLUMN,
                        QuoteColumns.ISCURRENT + " = ?",
                        new String[]{"1"},
                        null);

                Binder.restoreCallingIdentity(identityToken);

            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            // Construct RemoteViews object using position of a WidgetItem
            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position)) {
                    return null;
                }

                // Construct a RemoteViews based on the app widget item XML file
                RemoteViews views = new RemoteViews(getPackageName(),R.layout.stock_widget_list_item);
                String stockSymbol = data.getString(INDEX_SYMBOL);
                String bidPrice = data.getString(INDEX_BIDPRICE);
                String percentChange = data.getString(INDEX_PERCENT_CHANGE);

                views.setTextViewText(R.id.widget_stock_symbol, stockSymbol);
                views.setTextViewText(R.id.widget_bid_price, bidPrice);
                views.setTextViewText(R.id.widget_change, percentChange);

                setRemoteContentDescription(views, stockSymbol);

                // Set a fill-intent to be used to fill in the pending intent template that is
                // set on the collection view in the Widget Provider
                final Intent fillInIntent = new Intent(getApplicationContext(), MyStocksChart.class)
                        .putExtra(STOCK_SYMBOL, stockSymbol)
                        .putExtra(BID_PRICE, bidPrice)
                        .putExtra(PERCENT_CHANGE, percentChange);
                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);

                // return the RemoteViews object
                return views;
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.stock_widget_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position))
                    return data.getLong(INDEX_SYMBOL);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }

            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            private void setRemoteContentDescription(RemoteViews views, String description) {
                views.setContentDescription(R.id.widget_list_item, description);
            }

        };
    }

}
