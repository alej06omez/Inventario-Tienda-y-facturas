package com.tienda.inventario.validator;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Solo estructura (nulos, longitudes, formatos, rangos). Sin BD ni Sesion. Lanza AppException con el primer error. */
public final class Validador {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,50}$");
    private static final Pattern TELEFONO = Pattern.compile("^(?=(?:\\D*\\d){7})[0-9+ -]{7,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CEDULA = Pattern.compile("^[0-9A-Za-z-]{4,20}$");
    private static final BigDecimal PRECIO_MAX = new BigDecimal("9999999999.99"); // DECIMAL(12,2)
    // Cotas para que la suma de lineas fusionadas del mismo producto nunca desborde int.
    private static final int CANTIDAD_LINEA_MAX = 1_000_000, LINEAS_MAX = 1_000;

    private Validador() {}

    private static void req(boolean ok, String msg) { if (!ok) throw new AppException(msg); }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }

    private static boolean largo(String s, int min, int max) { return s != null && s.strip().length() >= min && s.length() <= max; }

    private static boolean fmt(Pattern p, String s) { return s != null && p.matcher(s).matches(); }

    /** null = campo opcional no llenado. */
    private static boolean opcional(Pattern p, String s, int max) { return s == null || (s.length() <= max && fmt(p, s)); }

    /** H-29: BCrypt ignora lo que pase de 72 bytes UTF-8. */
    private static boolean bytes72(String s) { return s.getBytes(StandardCharsets.UTF_8).length <= 72; }

    private static void password(String p) {
        req(p != null && p.length() >= 8 && bytes72(p)
                && p.chars().anyMatch(Character::isLetter) && p.chars().anyMatch(Character::isDigit),
                "La contraseña debe tener entre 8 y 72 caracteres (máx. 72 bytes: tildes y ñ cuentan doble), con letras y números");
    }

    private static void respuesta(String r) {
        req(largo(r, 1, 200) && bytes72(r), "La respuesta de recuperación es obligatoria (máx. 72 bytes)");
    }

    public static void usuario(Usuario u) {
        req(u != null, "Datos de usuario requeridos");
        req(largo(u.nombre(), 1, 100), "El nombre es obligatorio (máx. 100)");
        req(fmt(USERNAME, u.username()), "El usuario debe tener 3 a 50 letras, números, _ o .");
        password(u.password());
        req(fmt(TELEFONO, u.telefono()), "Teléfono inválido (7 a 20 dígitos, +, - o espacios)");
        req(largo(u.preguntaRecover(), 1, 200), "La pregunta de recuperación es obligatoria (máx. 200)");
        respuesta(u.respuestaRecover());
    }

    public static void cambioCredenciales(String actual, String nueva, String pregunta, String respuesta) {
        req(!vacio(actual), "Ingrese su contraseña actual");
        password(nueva);
        req(largo(pregunta, 1, 200), "La pregunta de recuperación es obligatoria (máx. 200)");
        respuesta(respuesta);
    }

    public static void login(String username, String password) {
        req(!vacio(username) && !vacio(password), "Ingrese usuario y contraseña");
    }

    public static void recuperacion(String username, String respuesta, String nuevaPassword) {
        req(!vacio(username) && !vacio(respuesta), "Ingrese usuario y respuesta");
        password(nuevaPassword);
    }

    public static void cajero(Cajero c) {
        req(c != null && largo(c.nombre(), 1, 100), "El nombre del cajero es obligatorio (máx. 100)");
        req(largo(c.apellido(), 1, 100), "El apellido del cajero es obligatorio (máx. 100)");
    }

    public static void distribuidora(Distribuidora d, Distribuidor dr) {
        req(d != null && largo(d.nombre(), 1, 120), "El nombre de la distribuidora es obligatorio (máx. 120)");
        req(opcional(EMAIL, d.email(), 120), "Email de la distribuidora inválido");
        req(opcional(TELEFONO, d.telefono(), 20), "Teléfono de la distribuidora inválido");
        if (dr == null) return;
        req(fmt(CEDULA, dr.cedula()), "Cédula del distribuidor inválida (4 a 20 letras, números o -)");
        req(dr.nombre() == null || largo(dr.nombre(), 1, 100), "El nombre del distribuidor es inválido (máx. 100)");
        req(dr.apellidos() == null || dr.apellidos().length() <= 100, "Apellidos del distribuidor muy largos (máx. 100)");
        req(opcional(EMAIL, dr.email(), 120), "Email del distribuidor inválido");
        req(opcional(TELEFONO, dr.telefono(), 20), "Teléfono del distribuidor inválido");
    }

    public static void categoria(Categoria c) {
        req(c != null && largo(c.nombre(), 1, 80), "El nombre de la categoría es obligatorio (máx. 80)");
        req(largo(c.tipo(), 1, 80), "El tipo de la categoría es obligatorio (máx. 80)");
        umbral(c.umbralStock());
    }

    public static void producto(Producto p) {
        req(p != null && largo(p.nombre(), 1, 120), "El nombre del producto es obligatorio (máx. 120)");
        req(p.distribuidoraId() > 0, "Seleccione una distribuidora");
        req(p.categoriaId() > 0, "Seleccione una categoría");
        req(p.cantidad() >= 0, "La cantidad no puede ser negativa");
        req(p.precio() != null && p.precio().signum() >= 0 && p.precio().compareTo(PRECIO_MAX) <= 0
                && p.precio().stripTrailingZeros().scale() <= 2, "Precio inválido (>= 0, máx. 2 decimales)");
        req(p.fechaExpiracion() != null, "Seleccione la fecha de expiración");
    }

    public static void linea(LineaVenta l) {
        req(l != null && l.productoId() > 0, "Seleccione un producto");
        req(l.cantidad() > 0 && l.cantidad() <= CANTIDAD_LINEA_MAX, "La cantidad debe ser mayor que 0 (máx. 1000000)");
    }

    public static void venta(Integer cajeroId, List<LineaVenta> lineas) {
        req(cajeroId != null, "Seleccione un cajero");
        req(lineas != null && !lineas.isEmpty(), "Agregue al menos un producto");
        req(lineas.size() <= LINEAS_MAX, "Demasiadas líneas en la venta (máx. 1000)");
        lineas.forEach(Validador::linea);
    }

    public static void umbral(int umbral) { req(umbral >= 1, "El umbral debe ser al menos 1"); }

    /** Texto de la UI -> int. */
    public static int entero(String s, String campo) {
        try {
            return Integer.parseInt(s.strip());
        } catch (RuntimeException e) {
            throw new AppException(campo + ": ingrese un número entero");
        }
    }

    /** Texto de la UI -> BigDecimal (acepta coma decimal). */
    public static BigDecimal decimal(String s, String campo) {
        try {
            return new BigDecimal(s.strip().replace(',', '.'));
        } catch (RuntimeException e) {
            throw new AppException(campo + ": ingrese un número");
        }
    }
}
