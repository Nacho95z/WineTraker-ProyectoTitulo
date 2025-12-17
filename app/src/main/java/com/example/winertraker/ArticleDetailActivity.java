package com.example.winertraker;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

public class ArticleDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        // 🔹 Toolbar tipo Fintual
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false); // importante
            getSupportActionBar().setTitle("");
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setAlpha(0f);// comienza invisible

        // 🔹 Efecto aparición al scrollear
        AppBarLayout appBar = findViewById(R.id.appBar);
        final float MIN_ALPHA = 0.3f;

        toolbar.setAlpha(MIN_ALPHA);

        appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScroll = appBarLayout.getTotalScrollRange();
            if (totalScroll == 0) return;

            float progress = Math.min(1f, Math.abs(verticalOffset) / (float) totalScroll);
            float alpha = MIN_ALPHA + (1f - MIN_ALPHA) * progress;

            toolbar.setAlpha(alpha);

            if (progress < 0.1f) {
                toolbar.setBackgroundColor(Color.parseColor("#33FFFFFF")); // blanco 20%
            } else if (progress > 0.6f) {
                toolbar.setBackgroundColor(Color.parseColor("#FAFAFA"));
            } else {
                toolbar.setBackgroundColor(Color.TRANSPARENT);
            }

        });



        // 🔹 Datos del artículo
        String title = getIntent().getStringExtra("title");
        String meta  = getIntent().getStringExtra("meta");
        String body  = getIntent().getStringExtra("body");
        String url   = getIntent().getStringExtra("url");

        TextView txtTitle = findViewById(R.id.txtDetailTitle);
        TextView txtMeta  = findViewById(R.id.txtDetailMeta);
        TextView txtBody  = findViewById(R.id.txtDetailBody);
        Button btnSource  = findViewById(R.id.btnOpenSource);
        ImageView imgHeader = findViewById(R.id.imgDetailHeader);

        // 🔹 Imagen destacada + fade-in
        int imageResId = getIntent().getIntExtra("imageResId", 0);
        if (imageResId != 0) {
            imgHeader.setAlpha(0f);
            imgHeader.setImageResource(imageResId);
            imgHeader.animate().alpha(1f).setDuration(250).start();
        } else {
            imgHeader.setVisibility(View.GONE);
        }

        txtTitle.setText(title != null ? title : "Artículo");
        txtMeta.setText(meta != null ? meta : "");
        txtBody.setText(body != null ? body : "");

        if (url == null || url.trim().isEmpty()) {
            btnSource.setVisibility(View.GONE);
        } else {
            btnSource.setOnClickListener(v ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            );
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
