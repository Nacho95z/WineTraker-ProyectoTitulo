package com.example.winertraker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class ArticleDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        // Botón atrás tipo “blog”
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Artículo");
        }

        String title = getIntent().getStringExtra("title");
        String meta  = getIntent().getStringExtra("meta");
        String body  = getIntent().getStringExtra("body");
        String url   = getIntent().getStringExtra("url");

        TextView txtTitle = findViewById(R.id.txtDetailTitle);
        TextView txtMeta  = findViewById(R.id.txtDetailMeta);
        TextView txtBody  = findViewById(R.id.txtDetailBody);
        Button btnSource  = findViewById(R.id.btnOpenSource);

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
