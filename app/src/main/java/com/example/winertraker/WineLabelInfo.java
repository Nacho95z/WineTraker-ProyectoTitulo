package com.example.winertraker;

public class WineLabelInfo {
    private String wineName;
    private String variety;
    private String vintage;
    private String origin;
    private String percentage;
    private String rawText; // opcional: descripción completa o texto detectado
    private String category; // opcional: categoría o línea
    private String comment;
    private String price;



    public String getWineName() {
        return wineName;
    }

    public void setWineName(String wineName) {
        this.wineName = wineName;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public String getVintage() {
        return vintage;
    }

    public void setVintage(String vintage) {
        this.vintage = vintage;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getPercentage() {
        return percentage;
    }

    public void setPercentage(String percentage) {
        this.percentage = percentage;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public void normalizeFields() {
        if (wineName == null) return;

        String name = wineName.trim();
        String cat  = category != null ? category.trim() : "";

        // Palabras típicas de categoría
        String[] knownCategories = {
                "gran reserva",
                "reserva",
                "selección",
                "estate",
                "limited edition",
                "special reserve"
        };

        for (String k : knownCategories) {
            String pattern = "\\b" + k + "\\b";
            if (name.toLowerCase().matches(".*" + pattern + ".*")) {

                // Si la categoría está vacía o duplicada
                if (cat.isEmpty() || !cat.toLowerCase().contains(k)) {
                    category = capitalize(k);
                }

                // Limpiar el nombre
                wineName = name.replaceAll("(?i)" + k, "")
                        .replaceAll("\\s{2,}", " ")
                        .trim();
                break;
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }

}

