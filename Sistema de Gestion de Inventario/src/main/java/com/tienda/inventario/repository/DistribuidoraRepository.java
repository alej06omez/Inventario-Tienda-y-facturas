package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.Distribuidor;
import com.tienda.inventario.model.Distribuidora;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class DistribuidoraRepository {
    private DistribuidoraRepository() {}

    /** Transaccion: distribuidor (si viene; cedula existente se reusa solo si los datos enviados coinciden) + distribuidora. */
    public static int registrar(Distribuidora d, Distribuidor dr) {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            // R-H19: READ COMMITTED -> el reintento lee el ultimo commit sin lectura bloqueante (sin ciclo S->X en InnoDB).
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                String cedula = dr == null ? d.distribuidorId() : existente(c, dr);
                if (dr != null && cedula == null) {
                    if (dr.nombre() == null)
                        throw new AppException("No existe un distribuidor con cédula " + dr.cedula() + "; ingrese su nombre para registrarlo");
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO distribuidor (cedula, nombre, apellidos, email, telefono) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, dr.cedula());
                        ps.setString(2, dr.nombre());
                        ps.setString(3, dr.apellidos());
                        ps.setString(4, dr.email());
                        ps.setString(5, dr.telefono());
                        ps.executeUpdate();
                        cedula = dr.cedula();
                    } catch (SQLIntegrityConstraintViolationException dup) {
                        // H-19: otra caja creo la cedula entre el SELECT y el INSERT; en READ COMMITTED el mismo SELECT ve su commit.
                        cedula = existente(c, dr);
                        if (cedula == null) throw dup;
                    }
                }
                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO distribuidora (nombre, telefono, email, distribuidor_id) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, d.nombre());
                    ps.setString(2, d.telefono());
                    ps.setString(3, d.email());
                    ps.setString(4, cedula);
                    ps.executeUpdate();
                    id = Db.generatedKey(ps);
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException r) { e.addSuppressed(r); }
                throw e;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("El distribuidor indicado no existe", e);
        } catch (SQLTransactionRollbackException e) {
            throw new AppException("Otra operación simultánea chocó con esta y se deshizo. Intente de nuevo", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    /** null = no hay fila; fila con datos distintos a los enviados -> AppException (H-05); iguales -> cedula tal como esta en BD (reuso). */
    private static String existente(Connection c, Distribuidor dr) throws SQLException {
        try (PreparedStatement sel = c.prepareStatement(
                "SELECT cedula, nombre, apellidos, email, telefono FROM distribuidor WHERE cedula = ?")) {
            sel.setString(1, dr.cedula());
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) return null;
                String nombre = rs.getString(2), apellidos = rs.getString(3);
                if (!(igual(dr.nombre(), nombre) && igual(dr.apellidos(), apellidos)
                        && igual(dr.email(), rs.getString(4)) && igual(dr.telefono(), rs.getString(5))))
                    throw new AppException("Ya existe un distribuidor con cédula " + dr.cedula() + " a nombre de " + nombre
                            + (apellidos != null ? " " + apellidos : "") + ". Use los mismos datos o deje solo la cédula para vincularlo");
                return rs.getString(1);
            }
        }
    }

    private static boolean igual(String enviado, String guardado) { return enviado == null || enviado.equalsIgnoreCase(guardado); }

    public static List<Distribuidora> listar() {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, nombre, telefono, email, distribuidor_id FROM distribuidora ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            List<Distribuidora> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }

    private static Distribuidora map(ResultSet rs) throws SQLException {
        return new Distribuidora(rs.getInt("id"), rs.getString("nombre"), rs.getString("telefono"),
                rs.getString("email"), rs.getString("distribuidor_id"));
    }
}
