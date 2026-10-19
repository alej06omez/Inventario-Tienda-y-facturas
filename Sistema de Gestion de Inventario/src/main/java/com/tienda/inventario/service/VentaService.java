package com.tienda.inventario.service;

import com.tienda.inventario.model.LineaVenta;
import com.tienda.inventario.repository.VentaRepository;
import com.tienda.inventario.validator.Validador;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VentaService {
    private VentaService() {}

    /** Fusiona lineas del mismo producto y las ordena por productoId (orden de bloqueo fijo en la transaccion). */
    public static int registrar(Integer cajeroId, List<LineaVenta> lineas) {
        Permisos.exigir();
        Validador.venta(cajeroId, lineas);
        List<LineaVenta> fusion = lineas.stream()
                .collect(Collectors.toMap(LineaVenta::productoId, LineaVenta::cantidad, Integer::sum, TreeMap::new))
                .entrySet().stream().map(e -> new LineaVenta(e.getKey(), e.getValue())).toList();
        return VentaRepository.registrar(cajeroId, fusion);
    }
}
