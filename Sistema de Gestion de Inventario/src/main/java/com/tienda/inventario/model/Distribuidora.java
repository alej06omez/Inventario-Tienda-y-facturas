package com.tienda.inventario.model;

public record Distribuidora(Integer id, String nombre, String telefono, String email, String distribuidorId) {
    @Override public String toString() { return nombre; }
}
