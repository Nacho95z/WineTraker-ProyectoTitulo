package com.example.winertraker;

public class Article {
    public String id;
    public String title;
    public String subtitle;
    public String source;
    public String readTime;
    public String content;
    public String url;

    public int imageResId; // 👈 nuevo

    // ✅ Constructor antiguo (el que ya tenías)
    public Article(String id, String title, String subtitle, String source, String readTime,
                   String content, String url) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.source = source;
        this.readTime = readTime;
        this.content = content;
        this.url = url;
        this.imageResId = 0; // default
    }

    // ✅ Constructor nuevo (con imagen local)
    public Article(String id, String title, String subtitle, String source, String readTime,
                   String content, String url, int imageResId) {
        this(id, title, subtitle, source, readTime, content, url);
        this.imageResId = imageResId;
    }
}
