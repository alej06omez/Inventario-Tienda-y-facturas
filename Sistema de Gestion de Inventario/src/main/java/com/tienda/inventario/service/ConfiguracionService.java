package com.tienda.inventario.service;

import java.util.prefs.Preferences;

/** Preferencia local de la maquina (no BD, sin permisos). */
public final class ConfiguracionService {
    private ConfiguracionService() {}

    public enum TamanoLetra {
        PEQUENA(12), MEDIANA(14), GRANDE(18);
        public final int px;
        TamanoLetra(int px) { this.px = px; }
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(ConfiguracionService.class);

    public static TamanoLetra tamano() {
        try {
            return TamanoLetra.valueOf(PREFS.get("tamanoLetra", TamanoLetra.MEDIANA.name()));
        } catch (IllegalArgumentException e) {
            return TamanoLetra.MEDIANA;
        }
    }

    public static void setTamano(TamanoLetra t) { PREFS.put("tamanoLetra", t.name()); }
}
