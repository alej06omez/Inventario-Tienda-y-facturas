package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.Producto;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ProductoRepository {
    private ProductoRepository() {}

    /** Fila para el dashboard: el repo no calcula niveles. */
    public record ProductoConCategoria(Producto p, String categoria, int umbral) {}

    public static int insertar(Producto p) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO producto (nombre, distribuidora_id, categoria_id, cantidad, precio, fecha_expiracion) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            campos(ps, p);
            ps.executeUpdate();
            return Db.generatedKey(ps);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("La distribuidora o la categoría no existe", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    /** H-23: solo escribe si el stock sigue siendo el que se leyo (una venta intermedia no se pisa). */
    // ponytail: los demas campos son last-write-wins entre dos editores; columna version INT si hace falta.
    public static void actualizar(Producto p, int cantidadLeida) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "UPDATE producto SET nombre = ?, distribuidora_id = ?, categoria_id = ?, cantidad = ?, precio = ?, fecha_expiracion = ? WHERE id = ? AND cantidad = ?")) {
            campos(ps, p);
            ps.setInt(7, p.id());
            ps.setInt(8, cantidadLeida);
            if (ps.executeUpdate() > 0) return;
            try (PreparedStatement sel = c.prepareStatement("SELECT nombre, cantidad FROM producto WHERE id = ?")) {
                sel.setInt(1, p.id());
                try (ResultSet rs = sel.executeQuery()) {
                    // H-27: InventarioController.actualizar recarga segun estos textos ("El stock de " / "Producto no encontrado"); si cambian, cambiar alli y en los tests h23.
                    if (!rs.next()) throw new AppException("Producto no encontrado");
                    throw new AppException("El stock de \"" + rs.getString(1) + "\" cambió a " + rs.getInt(2)
                            + " mientras lo editaba (p. ej. una venta). Revise los datos y vuelva a actualizar");
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("La distribuidora o la categoría no existe", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static void eliminar(int id) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM producto WHERE id = ?")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new AppException("Producto no encontrado");
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static List<Producto> listar() {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("""
                SELECT id, nombre, distribuidora_id, categoria_id, cantidad, precio, fecha_registro, fecha_actualizacion, fecha_expiracion
                FROM producto ORDER BY nombre""");
             ResultSet rs = ps.executeQuery()) {
            List<Producto> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    /** Solo candidatos del dashboard (por expirar en <= 7 dias o stock bajo), filtrado en SQL. */
    public static List<ProductoConCategoria> listarConCategoria() {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("""
                SELECT p.id, p.nombre, p.distribuidora_id, p.categoria_id, p.cantidad, p.precio, p.fecha_registro,
                       p.fecha_actualizacion, p.fecha_expiracion, c.nombre AS categoria, c.umbral_stock
                FROM producto p JOIN categoria c ON c.id = p.categoria_id
                WHERE p.fecha_expiracion <= ? OR p.cantidad < c.umbral_stock OR p.cantidad <= 1""")) {
            ps.setObject(1, LocalDate.now().plusDays(7));
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductoConCategoria> out = new ArrayList<>();
                while (rs.next()) out.add(new ProductoConCategoria(map(rs), rs.getString("categoria"), rs.getInt("umbral_stock")));
                return out;
            }
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    private static void campos(PreparedStatement ps, Producto p) throws SQLException {
        ps.setString(1, p.nombre());
        ps.setInt(2, p.distribuidoraId());
        ps.setInt(3, p.categoriaId());
        ps.setInt(4, p.cantidad());
        ps.setBigDecimal(5, p.precio());
        ps.setObject(6, p.fechaExpiracion());
    }

    private static Producto map(ResultSet rs) throws SQLException {
        return new Producto(rs.getInt("id"), rs.getString("nombre"), rs.getInt("distribuidora_id"), rs.getInt("categoria_id"),
                rs.getInt("cantidad"), rs.getBigDecimal("precio"), rs.getObject("fecha_registro", LocalDateTime.class),
                rs.getObject("fecha_actualizacion", LocalDateTime.class), rs.getObject("fecha_expiracion", LocalDate.class));
    }
}
