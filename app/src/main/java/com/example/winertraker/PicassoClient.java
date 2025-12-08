package com.example.winertraker;

import android.content.Context;

import com.squareup.picasso.LruCache;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;

public class PicassoClient {

    private static Picasso picassoInstance = null;

    public static Picasso getInstance(Context context) {
        if (picassoInstance == null) {

            // Tamaño del caché en memoria (20 MB)
            LruCache memoryCache = new LruCache(20 * 1024 * 1024);

            // Caché en disco (100 MB)
            File cacheDir = new File(context.getCacheDir(), "picasso-cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            okhttp3.Cache diskCache = new okhttp3.Cache(cacheDir, 100 * 1024 * 1024);

            OkHttp3Downloader downloader = new OkHttp3Downloader(
                    new okhttp3.OkHttpClient.Builder()
                            .cache(diskCache)
                            .build()
            );

            picassoInstance = new Picasso.Builder(context)
                    .downloader(downloader)
                    .memoryCache(memoryCache)
                    .build();

            Picasso.setSingletonInstance(picassoInstance);
        }

        return picassoInstance;
    }
}
