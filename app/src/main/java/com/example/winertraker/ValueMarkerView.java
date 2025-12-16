package com.example.winertraker;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

public class ValueMarkerView extends MarkerView {

    private final TextView txt;
    private final String[] months;

    public interface Formatter {
        String format(float value);
    }

    private final Formatter formatter;

    public ValueMarkerView(Context context, int layoutResource, String[] months, Formatter formatter) {
        super(context, layoutResource);
        this.txt = findViewById(R.id.txtMarker);
        this.months = months;
        this.formatter = formatter;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int monthIndex = (int) e.getX();
        String month = (months != null && monthIndex >= 0 && monthIndex < months.length) ? months[monthIndex] : "";
        txt.setText(month + ": " + formatter.format(e.getY()));
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Centrado horizontal y arriba del punto
//        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
        return new MPPointF(-(getWidth() * 0.9f), -getHeight() - 10);

    }
}
