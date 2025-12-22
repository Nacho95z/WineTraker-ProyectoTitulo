package com.example.winertraker;

import java.util.Calendar;
import java.util.Locale;

public class PeakWindowCalculator {

    public static class PeakWindow {
        public final int startYear;
        public final int endYear;
        public final String message;

        public PeakWindow(int startYear, int endYear, String message) {
            this.startYear = startYear;
            this.endYear = endYear;
            this.message = message;
        }
    }

    /**
     * Heurística simple (defendible en tesis): calcula ventana óptima en AÑOS
     * según variedad + categoría (Reserva/Gran Reserva) + cosecha.
     */
    public static PeakWindow calculate(String variety, String category, int vintageYear) {

        String v = (variety == null) ? "" : variety.trim().toLowerCase(Locale.ROOT);
        String c = (category == null) ? "" : category.trim().toLowerCase(Locale.ROOT);

        // Base (startOffset, endOffset) en años desde la cosecha
        int startOffset = 1;
        int endOffset = 3;

        // --- Variedad ---
        if (containsAny(v, "cabernet sauvignon", "cabernet")) { startOffset = 4; endOffset = 10; }
        else if (containsAny(v, "carmenere", "carménère", "carmenère")) { startOffset = 3; endOffset = 8; }
        else if (containsAny(v, "syrah", "shiraz")) { startOffset = 2; endOffset = 6; }
        else if (containsAny(v, "merlot")) { startOffset = 2; endOffset = 6; }
        else if (containsAny(v, "malbec")) { startOffset = 2; endOffset = 7; }
        else if (containsAny(v, "pinot noir", "pinot")) { startOffset = 1; endOffset = 4; }
        else if (containsAny(v, "chardonnay")) { startOffset = 0; endOffset = 3; }
        else if (containsAny(v, "sauvignon blanc")) { startOffset = 0; endOffset = 2; }

        // --- Categoría (modificadores) ---
        // Gran Reserva tiende a aguantar más.
        if (c.contains("gran reserva")) {
            endOffset += 2;
        } else if (c.contains("reserva")) {
            endOffset += 1;
        }
        // Ediciones/selecciones a veces apuntan a mejor guarda
        if (containsAny(c, "limited", "edición", "edicion", "special reserve", "selección", "seleccion")) {
            endOffset += 1;
        }

        // Sanitizar: endOffset >= startOffset
        if (endOffset < startOffset) endOffset = startOffset;

        int startYear = vintageYear + startOffset;
        int endYear = vintageYear + endOffset;

        int nowYear = Calendar.getInstance().get(Calendar.YEAR);

        String message;
        if (nowYear < startYear) {
            int yearsLeft = startYear - nowYear;
            message = "Este vino alcanzará su mejor momento entre " + startYear + " y " + endYear +
                    " (faltan ~" + yearsLeft + " año(s) para entrar en apogeo).";
        } else if (nowYear <= endYear) {
            message = "Este vino está en su ventana óptima: " + startYear + " – " + endYear + ".";
        } else {
            message = "La ventana óptima estimada fue " + startYear + " – " + endYear;
        }

        return new PeakWindow(startYear, endYear, message);
    }

    private static boolean containsAny(String base, String... keys) {
        if (base == null) return false;
        for (String k : keys) {
            if (k != null && !k.isEmpty() && base.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
