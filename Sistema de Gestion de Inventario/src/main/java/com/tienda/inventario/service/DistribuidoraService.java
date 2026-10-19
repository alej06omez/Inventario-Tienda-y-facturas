package com.tienda.inventario.service;

import com.tienda.inventario.model.Distribuidor;
import com.tienda.inventario.model.Distribuidora;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.repository.DistribuidoraRepository;
import com.tienda.inventario.validator.Validador;

import java.util.List;

public final class DistribuidoraService {
    private DistribuidoraService() {}

    /** Strings vacios -> null; distribuidor con todos los campos vacios -> no se registra. */
    public static int registrar(Distribuidora d, Distribuidor dr) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        if (d != null) d = new Distribuidora(null, n(d.nombre()), n(d.telefono()), n(d.email()), n(d.distribuidorId()));
        if (dr != null) dr = new Distribuidor(n(dr.cedula()), n(dr.nombre()), n(dr.apellidos()), n(dr.email()), n(dr.telefono()));
        if (dr != null && dr.cedula() == null && dr.nombre() == null && dr.apellidos() == null && dr.email() == null && dr.telefono() == null) dr = null;
        Validador.distribuidora(d, dr);
        return DistribuidoraRepository.registrar(d, dr);
    }

    public static List<Distribuidora> listar() {
        Permisos.exigir();
        return DistribuidoraRepository.listar();
    }

    private static String n(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
