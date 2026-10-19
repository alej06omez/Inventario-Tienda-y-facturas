package com.tienda.inventario.service;

import com.tienda.inventario.model.Cajero;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.repository.CajeroRepository;
import com.tienda.inventario.validator.Validador;

import java.util.List;

public final class CajeroService {
    private CajeroService() {}

    public static int registrar(Cajero c) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        Validador.cajero(c);
        return CajeroRepository.insertar(new Cajero(null, c.nombre().strip(), c.apellido().strip()));
    }

    public static List<Cajero> listar() {
        Permisos.exigir();
        return CajeroRepository.listar();
    }
}
