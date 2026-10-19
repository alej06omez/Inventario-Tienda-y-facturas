package com.tienda.inventario.model;

public record Cajero(Integer id, String nombre, String apellido) {
    @Override public String toString() { return nombre + " " + apellido; }
}
