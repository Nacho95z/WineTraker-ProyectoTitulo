package com.example.winertraker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;

public class ChartsPagerAdapter extends RecyclerView.Adapter<ChartsPagerAdapter.VH> {

    public interface Binder {
        void bindPie(PieChart chart);
        void bindBar(BarChart chart);
        void bindLine(LineChart chart);
    }

    private final Binder binder;

    public ChartsPagerAdapter(Binder binder) {
        this.binder = binder;
    }

    @Override public int getItemCount() { return 3; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chart_page, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.container.removeAllViews();

        // ✅ Título arriba, fuera del área del chart
        if (position == 0) holder.title.setText("Distribución por Variedad");
        else if (position == 1) holder.title.setText("Histórico de Botellas Registradas");
        else holder.title.setText("Evolución del Valor de la Bodega");

        if (position == 0) {
            PieChart c = new PieChart(holder.itemView.getContext());
            c.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            holder.container.addView(c);
            binder.bindPie(c);

        } else if (position == 1) {
            BarChart c = new BarChart(holder.itemView.getContext());
            c.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            holder.container.addView(c);
            binder.bindBar(c);

        } else {
            LineChart c = new LineChart(holder.itemView.getContext());
            c.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            holder.container.addView(c);
            binder.bindLine(c);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        FrameLayout container;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txtChartTitleInsideCard);
            container = itemView.findViewById(R.id.chartContainer);
        }
    }
}
