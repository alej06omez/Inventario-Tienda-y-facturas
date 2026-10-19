package com.tienda.inventario.service;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Producto;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.repository.ProductoRepository;
import com.tienda.inventario.validator.Validador;

import java.util.List;

public final class ProductoService {
    private ProductoService() {}

    public static int registrar(Producto p) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        Validador.producto(p);
        return ProductoRepository.insertar(limpio(p));
    }

    public static void actualizar(Producto p, int cantidadLeida) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        Validador.producto(p);
        if (p.id() == null) throw new AppException("Seleccione un producto");
        ProductoRepository.actualizar(limpio(p), cantidadLeida);
    }

    public static void eliminar(int id) {
        Permisos.exigir(Rol.ADMINISTRADOR, Rol.DUENO);
        ProductoRepository.eliminar(id);
    }

    public static List<Producto> listar() {
        Permisos.exigir();
        return ProductoRepository.listar();
    }

    private static Producto limpio(Producto p) {
        return new Producto(p.id(), p.nombre().strip(), p.distribuidoraId(), p.categoriaId(), p.cantidad(), p.precio(),
                null, null, p.fechaExpiracion());
    }
}
