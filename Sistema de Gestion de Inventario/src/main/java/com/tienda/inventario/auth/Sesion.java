package com.tienda.inventario.auth;

import com.tienda.inventario.model.Usuario;

/** Usuario logueado (app de escritorio: uno a la vez). Nunca guarda hashes. */
public final class Sesion {
    private static Usuario actual;

    private Sesion() {}

    public static Usuario actual() { return actual; }

    public static void iniciar(Usuario u) {
        actual = new Usuario(u.id(), u.nombre(), u.username(), null, u.telefono(), u.preguntaRecover(), null, u.rol());
    }

    public static void cerrar() { actual = null; }
}
