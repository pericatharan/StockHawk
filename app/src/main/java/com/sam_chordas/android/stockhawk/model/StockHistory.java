package com.sam_chordas.android.stockhawk.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This StockHistory Class is used to handle the relevant historical data of a stock.
 * The stock symbol, date and closing price is retrieved from JSON and stored.
 * Created by maile on 8/20/2016.
 */
public class StockHistory implements Parcelable {
    public String mSymbol;
    public String mDate;
    public String mClose;

    public StockHistory(String mSymbol, String mDate, String mClose) {
        this.mSymbol = mSymbol;
        this.mDate = mDate;
        this.mClose = mClose;
    }

    protected StockHistory(Parcel in) {
        mSymbol = in.readString();
        mDate = in.readString();
        mClose = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSymbol);
        dest.writeString(mDate);
        dest.writeString(mClose);
    }

    public String getmSymbol() {
        return mSymbol;
    }

    public String getmDate() {
        return mDate;
    }

    public String getmClose() {
        return mClose;
    }

    public static final Creator<StockHistory> CREATOR = new Creator<StockHistory>() {
        @Override
        public StockHistory createFromParcel(Parcel in) {
            return new StockHistory(in);
        }

        @Override
        public StockHistory[] newArray(int size) {
            return new StockHistory[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
