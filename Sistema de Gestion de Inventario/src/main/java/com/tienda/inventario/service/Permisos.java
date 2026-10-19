package com.tienda.inventario.service;

import com.tienda.inventario.AppException;
import com.tienda.inventario.auth.Sesion;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.model.Usuario;

import java.util.List;

public final class Permisos {
    private Permisos() {}

    /** Sin argumentos = cualquier usuario logueado. */
    public static Usuario exigir(Rol... permitidos) {
        Usuario u = Sesion.actual();
        if (u == null) throw new AppException("Debe iniciar sesión");
        if (permitidos.length > 0 && !List.of(permitidos).contains(u.rol())) throw new AppException("No tiene permisos para esta acción");
        return u;
    }
}
