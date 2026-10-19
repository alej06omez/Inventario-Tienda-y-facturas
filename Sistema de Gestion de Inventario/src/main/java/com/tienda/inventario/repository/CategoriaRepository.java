package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.Categoria;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class CategoriaRepository {
    private CategoriaRepository() {}

    public static int insertar(Categoria cat) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categoria (nombre, tipo, umbral_stock) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cat.nombre());
            ps.setString(2, cat.tipo());
            ps.setInt(3, cat.umbralStock());
            ps.executeUpdate();
            return Db.generatedKey(ps);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("La categoría ya existe", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static List<Categoria> listar() {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, nombre, tipo, registro, umbral_stock FROM categoria ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            List<Categoria> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static void actualizarUmbral(int id, int umbral) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("UPDATE categoria SET umbral_stock = ? WHERE id = ?")) {
            ps.setInt(1, umbral);
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) throw new AppException("Categoría no encontrada");
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    private static Categoria map(ResultSet rs) throws SQLException {
        return new Categoria(rs.getInt("id"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getObject("registro", LocalDateTime.class), rs.getInt("umbral_stock"));
    }
}
