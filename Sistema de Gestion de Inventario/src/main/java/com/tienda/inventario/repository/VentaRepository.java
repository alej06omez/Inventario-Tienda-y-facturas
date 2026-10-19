package com.tienda.inventario.repository;

import com.tienda.inventario.AppException;
import com.tienda.inventario.Db;
import com.tienda.inventario.model.LineaVenta;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class VentaRepository {
    private VentaRepository() {}

    /**
     * Una transaccion (ARQUITECTURA.md sec. 9). Lineas ya fusionadas y ordenadas por productoId (orden de bloqueo fijo).
     * Stock: UPDATE condicional atomico. Nombre y precio: snapshot leido de BD, nunca de la UI.
     */
    public static int registrar(int cajeroId, List<LineaVenta> lineas) {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement upd = c.prepareStatement("UPDATE producto SET cantidad = cantidad - ? WHERE id = ? AND cantidad >= ?");
                 PreparedStatement sel = c.prepareStatement("SELECT nombre, precio, cantidad FROM producto WHERE id = ?");
                 PreparedStatement insVenta = c.prepareStatement("INSERT INTO venta (cajero_id, total) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement insLinea = c.prepareStatement(
                         "INSERT INTO producto_vendido (venta_id, producto_id, nombre_snapshot, precio_snapshot, cantidad) VALUES (?, ?, ?, ?, ?)")) {
                List<String> nombres = new ArrayList<>();
                List<BigDecimal> precios = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (LineaVenta l : lineas) {
                    upd.setInt(1, l.cantidad());
                    upd.setInt(2, l.productoId());
                    upd.setInt(3, l.cantidad());
                    int filas = upd.executeUpdate();
                    sel.setInt(1, l.productoId());
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) throw new AppException("Producto no encontrado (id " + l.productoId() + ")");
                        if (filas == 0) throw new AppException("Stock insuficiente para \"" + rs.getString(1) + "\" (disponible: " + rs.getInt(3) + ")");
                        nombres.add(rs.getString(1));
                        precios.add(rs.getBigDecimal(2));
                    }
                    total = total.add(precios.getLast().multiply(BigDecimal.valueOf(l.cantidad())));
                }
                insVenta.setInt(1, cajeroId);
                insVenta.setBigDecimal(2, total);
                insVenta.executeUpdate();
                int ventaId = Db.generatedKey(insVenta);
                for (int i = 0; i < lineas.size(); i++) {
                    insLinea.setInt(1, ventaId);
                    insLinea.setInt(2, lineas.get(i).productoId());
                    insLinea.setString(3, nombres.get(i));
                    insLinea.setBigDecimal(4, precios.get(i));
                    insLinea.setInt(5, lineas.get(i).cantidad());
                    insLinea.addBatch();
                }
                insLinea.executeBatch();
                c.commit();
                return ventaId;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException r) { e.addSuppressed(r); }
                throw e;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("El cajero no existe", e);
        } catch (SQLTransactionRollbackException e) {
            throw new AppException("Otra operación simultánea chocó con esta y se deshizo. Intente de nuevo", e);
        } catch (SQLException e) {
            throw new AppException("Error de base de datos", e);
        }
    }
}
