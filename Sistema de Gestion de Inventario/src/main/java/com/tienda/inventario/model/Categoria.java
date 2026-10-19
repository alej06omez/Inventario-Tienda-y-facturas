package com.tienda.inventario.model;

import java.time.LocalDateTime;

public record Categoria(Integer id, String nombre, String tipo, LocalDateTime registro, int umbralStock) {
    @Override public String toString() { return nombre; }
}
