package com.tienda.inventario.service;

import com.tienda.inventario.model.AlertaProducto;
import com.tienda.inventario.model.Nivel;
import com.tienda.inventario.repository.ProductoRepository;
import com.tienda.inventario.repository.ProductoRepository.ProductoConCategoria;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class DashboardService {
    private DashboardService() {}

    /** ROJO si ya expiro; AMARILLO si expira entre hoy y hoy+7; null = no se lista. */
    public static Nivel nivelExpiracion(LocalDate exp, LocalDate hoy) {
        if (exp.isBefore(hoy)) return Nivel.ROJO;
        if (!exp.isAfter(hoy.plusDays(7))) return Nivel.AMARILLO;
        return null;
    }

    /** ROJO si cantidad <= 1; AMARILLO si 1 < cantidad < umbral; null = no se lista. */
    public static Nivel nivelStock(int cantidad, int umbral) {
        if (cantidad <= 1) return Nivel.ROJO;
        if (cantidad < umbral) return Nivel.AMARILLO;
        return null;
    }

    public static List<AlertaProducto> porExpirar() {
        Permisos.exigir();
        LocalDate hoy = LocalDate.now();
        return alertas(pc -> nivelExpiracion(pc.p().fechaExpiracion(), hoy), Comparator.comparing(a -> a.producto().fechaExpiracion()));
    }

    public static List<AlertaProducto> stockBajo() {
        Permisos.exigir();
        return alertas(pc -> nivelStock(pc.p().cantidad(), pc.umbral()), Comparator.comparingInt(a -> a.producto().cantidad()));
    }

    /** ROJO primero, luego el orden dado. */
    private static List<AlertaProducto> alertas(Function<ProductoConCategoria, Nivel> nivel, Comparator<AlertaProducto> orden) {
        return ProductoRepository.listarConCategoria().stream()
                .map(pc -> new AlertaProducto(pc.p(), pc.categoria(), nivel.apply(pc)))
                .filter(a -> a.nivel() != null)
                .sorted(Comparator.comparing((AlertaProducto a) -> a.nivel() != Nivel.ROJO).thenComparing(orden))
                .toList();
    }
}
