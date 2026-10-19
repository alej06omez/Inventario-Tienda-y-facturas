package com.tienda.inventario.service;

import com.tienda.inventario.model.Categoria;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.repository.CategoriaRepository;
import com.tienda.inventario.validator.Validador;

import java.util.List;

public final class CategoriaService {
    private CategoriaService() {}

    public static int registrar(Categoria c) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        Validador.categoria(c);
        return CategoriaRepository.insertar(new Categoria(null, c.nombre().strip(), c.tipo().strip(), null, c.umbralStock()));
    }

    public static List<Categoria> listar() {
        Permisos.exigir();
        return CategoriaRepository.listar();
    }

    public static void actualizarUmbral(int categoriaId, int umbral) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        Validador.umbral(umbral);
        CategoriaRepository.actualizarUmbral(categoriaId, umbral);
    }
}
