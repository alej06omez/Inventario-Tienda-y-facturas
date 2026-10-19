package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.model.Usuario;

import java.sql.*;
import java.util.Optional;

public final class UsuarioRepository {
    private UsuarioRepository() {}

    public static Optional<Usuario> porUsername(String username) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, nombre, username, password, telefono, pregunta_recover, respuesta_recover, rol FROM usuario WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    /** Recibe password y respuesta ya hasheadas. */
    public static int insertar(Usuario u) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO usuario (nombre, username, password, telefono, pregunta_recover, respuesta_recover, rol) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.nombre());
            ps.setString(2, u.username());
            ps.setString(3, u.password());
            ps.setString(4, u.telefono());
            ps.setString(5, u.preguntaRecover());
            ps.setString(6, u.respuestaRecover());
            ps.setString(7, u.rol().name());
            ps.executeUpdate();
            return Db.generatedKey(ps);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("El username ya existe", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    public static void actualizarPassword(int id, String hash) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("UPDATE usuario SET password = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    /** Recibe password y respuesta ya hasheadas. */
    public static void actualizarCredenciales(int id, String hashPassword, String pregunta, String hashRespuesta) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "UPDATE usuario SET password = ?, pregunta_recover = ?, respuesta_recover = ? WHERE id = ?")) {
            ps.setString(1, hashPassword);
            ps.setString(2, pregunta);
            ps.setString(3, hashRespuesta);
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    private static Usuario map(ResultSet rs) throws SQLException {
        return new Usuario(rs.getInt("id"), rs.getString("nombre"), rs.getString("username"), rs.getString("password"),
                rs.getString("telefono"), rs.getString("pregunta_recover"), rs.getString("respuesta_recover"),
                Rol.valueOf(rs.getString("rol")));
    }
}
