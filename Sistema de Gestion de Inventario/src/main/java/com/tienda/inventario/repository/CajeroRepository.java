package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.Cajero;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class CajeroRepository {
    private CajeroRepository() {}

    public static int insertar(Cajero cj) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cajero (nombre, apellido) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cj.nombre());
            ps.setString(2, cj.apellido());
            ps.executeUpdate();
            return Db.generatedKey(ps);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static List<Cajero> listar() {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, nombre, apellido FROM cajero ORDER BY nombre, apellido");
             ResultSet rs = ps.executeQuery()) {
            List<Cajero> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    private static Cajero map(ResultSet rs) throws SQLException {
        return new Cajero(rs.getInt("id"), rs.getString("nombre"), rs.getString("apellido"));
    }
}
