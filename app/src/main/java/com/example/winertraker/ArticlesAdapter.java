package com.example.winertraker;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ArticlesAdapter extends RecyclerView.Adapter<ArticlesAdapter.VH> {

    private final List<Article> items;

    public ArticlesAdapter(List<Article> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_article, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Article a = items.get(position);

        h.txtTitle.setText(a.title);
        h.txtSubtitle.setText(a.subtitle);
        h.txtSource.setText(a.source + " • " + a.readTime);

        // 🖼️ Imagen
        if (a.imageResId != 0) {
            h.imgThumb.setImageResource(a.imageResId);
        } else {
            h.imgThumb.setImageResource(R.drawable.ic_wine);
        }

        h.itemView.setOnClickListener(v -> {
            Context ctx = v.getContext();
            Intent i = new Intent(ctx, ArticleDetailActivity.class);
            i.putExtra("title", a.title);
            i.putExtra("meta", a.source + " • " + a.readTime);
            i.putExtra("body", a.content);
            i.putExtra("url", a.url);
            ctx.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    // ✅ ÚNICA CLASE VH
    static class VH extends RecyclerView.ViewHolder {
        TextView txtTitle, txtSubtitle, txtSource;
        ImageView imgThumb;

        VH(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtSubtitle = itemView.findViewById(R.id.txtSubtitle);
            txtSource = itemView.findViewById(R.id.txtSource);
            imgThumb = itemView.findViewById(R.id.imgThumb);
        }
    }
}
