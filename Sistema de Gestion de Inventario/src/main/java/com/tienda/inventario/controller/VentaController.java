package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Cajero;
import com.tienda.inventario.model.LineaVenta;
import com.tienda.inventario.model.Producto;
import com.tienda.inventario.service.CajeroService;
import com.tienda.inventario.service.ProductoService;
import com.tienda.inventario.service.VentaService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VentaController {
    @FXML private ComboBox<Cajero> cajero;
    @FXML private ComboBox<Producto> producto;
    @FXML private TextField cantidad;
    @FXML private TableView<LineaVenta> tabla;
    @FXML private TableColumn<LineaVenta, String> colProducto;
    @FXML private TableColumn<LineaVenta, Integer> colCantidad;
    @FXML private TableColumn<LineaVenta, BigDecimal> colPrecio, colSubtotal;
    @FXML private Label total;

    private final ObservableList<LineaVenta> lineas = FXCollections.observableArrayList();
    private final Map<Integer, Producto> porId = new HashMap<>();
    private Runnable alCambiar = () -> {};

    public void setAlCambiar(Runnable r) { alCambiar = r; }

    @FXML
    private void initialize() {
        tabla.setItems(lineas);
        WindowManager.promptGris(cajero, producto);
        colProducto.setCellValueFactory(c -> new SimpleObjectProperty<>(nombre(c.getValue())));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().cantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(precio(c.getValue())));
        colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(subtotal(c.getValue())));
        WindowManager.columnaMoneda(colPrecio);
        WindowManager.columnaMoneda(colSubtotal);
    }

    public void refrescar() {
        try {
            cajero.getItems().setAll(CajeroService.listar());
            List<Producto> ps = ProductoService.listar();
            producto.getItems().setAll(ps);
            porId.clear();
            ps.forEach(p -> porId.put(p.id(), p));
            tabla.refresh();
            actualizarTotal();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void agregarLinea() {
        try {
            Producto p = producto.getValue();
            LineaVenta l = new LineaVenta(p == null ? 0 : p.id(), Validador.entero(cantidad.getText(), "Cantidad"));
            Validador.linea(l);
            lineas.add(l);
            cantidad.clear();
            actualizarTotal();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void quitarLinea() {
        lineas.remove(tabla.getSelectionModel().getSelectedItem());
        actualizarTotal();
    }

    @FXML
    private void registrarVenta() {
        try {
            Integer cajeroId = cajero.getValue() == null ? null : cajero.getValue().id();
            Validador.venta(cajeroId, lineas);
            if (!WindowManager.confirmar("¿Registrar la venta de " + lineas.size() + " línea(s) a cargo de " + cajero.getValue()
                    + "?" + System.lineSeparator() + total.getText())) return;
            int id = VentaService.registrar(cajeroId, List.copyOf(lineas));
            WindowManager.info("Venta #" + id + " registrada");
            lineas.clear();
            refrescar();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void nuevoCajero() {
        try {
            CajeroController c = WindowManager.abrir("cajero.fxml", "Nuevo cajero");
            if (c != null) c.setAlGuardar(this::refrescar);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    // Precio de referencia solo para mostrar: el total real lo calcula VentaRepository desde la BD.
    private String nombre(LineaVenta l) { Producto p = porId.get(l.productoId()); return p == null ? "#" + l.productoId() : p.nombre(); }

    private BigDecimal precio(LineaVenta l) { Producto p = porId.get(l.productoId()); return p == null ? BigDecimal.ZERO : p.precio(); }

    private BigDecimal subtotal(LineaVenta l) { return precio(l).multiply(BigDecimal.valueOf(l.cantidad())); }

    private void actualizarTotal() {
        total.setText("Total (referencia): " + WindowManager.moneda(lineas.stream().map(this::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
}
