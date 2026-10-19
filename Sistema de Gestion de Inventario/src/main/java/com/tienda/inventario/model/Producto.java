package com.tienda.inventario.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Producto(Integer id, String nombre, int distribuidoraId, int categoriaId, int cantidad, BigDecimal precio,
                       LocalDateTime fechaRegistro, LocalDateTime fechaActualizacion, LocalDate fechaExpiracion) {
    @Override public String toString() { return nombre + " (" + cantidad + ")"; }
}
