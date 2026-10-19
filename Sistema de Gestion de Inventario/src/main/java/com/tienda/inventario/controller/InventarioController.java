package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Categoria;
import com.tienda.inventario.model.Distribuidora;
import com.tienda.inventario.model.Producto;
import com.tienda.inventario.service.CategoriaService;
import com.tienda.inventario.service.DistribuidoraService;
import com.tienda.inventario.service.ProductoService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

public class InventarioController {
    @FXML private TableView<Producto> tabla;
    @FXML private TableColumn<Producto, Integer> colId, colCantidad;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, BigDecimal> colPrecio;
    @FXML private TableColumn<Producto, LocalDate> colExpiracion;
    @FXML private TextField nombre, cantidad, precio;
    @FXML private DatePicker expiracion;
    @FXML private ComboBox<Distribuidora> distribuidora;
    @FXML private ComboBox<Categoria> categoria;
    @FXML private Button actualizar, eliminar;

    private Runnable alCambiar = () -> {};

    public void setAlCambiar(Runnable r) { alCambiar = r; }

    @FXML
    private void initialize() {
        col(colId, Producto::id);
        col(colNombre, Producto::nombre);
        col(colCantidad, Producto::cantidad);
        col(colPrecio, Producto::precio);
        col(colExpiracion, Producto::fechaExpiracion);
        WindowManager.columnaMoneda(colPrecio);
        WindowManager.columnaFecha(colExpiracion);
        WindowManager.formatoFecha(expiracion);
        WindowManager.promptGris(distribuidora, categoria);
        actualizar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        eliminar.disableProperty().bind(actualizar.disableProperty());
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, p) -> cargar(p));
    }

    private static <T> void col(TableColumn<Producto, T> c, Function<Producto, T> f) {
        c.setCellValueFactory(cd -> new SimpleObjectProperty<>(f.apply(cd.getValue())));
    }

    public void refrescar() {
        try {
            recargar();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    private void recargar() {
        distribuidora.getItems().setAll(DistribuidoraService.listar());
        categoria.getItems().setAll(CategoriaService.listar());
        Producto sel = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(ProductoService.listar());
        // setAll pierde la seleccion: se recupera por id (si el producto sigue existiendo).
        if (sel != null) tabla.getItems().stream().filter(p -> p.id().equals(sel.id())).findFirst()
                .ifPresent(p -> tabla.getSelectionModel().select(p));
    }

    private void cargar(Producto p) {
        if (p == null) return;
        nombre.setText(p.nombre());
        cantidad.setText(String.valueOf(p.cantidad()));
        precio.setText(p.precio().toPlainString());
        expiracion.setValue(p.fechaExpiracion());
        distribuidora.setValue(distribuidora.getItems().stream().filter(d -> d.id() == p.distribuidoraId()).findFirst().orElse(null));
        categoria.setValue(categoria.getItems().stream().filter(c -> c.id() == p.categoriaId()).findFirst().orElse(null));
    }

    /** Producto armado desde los campos (combo vacio -> 0, el Validador lo rechaza). id null = nuevo. */
    private Producto leer(Integer id) {
        return new Producto(id, nombre.getText(),
                distribuidora.getValue() == null ? 0 : distribuidora.getValue().id(),
                categoria.getValue() == null ? 0 : categoria.getValue().id(),
                Validador.entero(cantidad.getText(), "Cantidad"), Validador.decimal(precio.getText(), "Precio"),
                null, null, expiracion.getValue());
    }

    @FXML
    private void nuevo() {
        tabla.getSelectionModel().clearSelection();
        nombre.clear();
        cantidad.clear();
        precio.clear();
        expiracion.setValue(null);
        distribuidora.setValue(null);
        categoria.setValue(null);
    }

    @FXML
    private void registrar() {
        try {
            Producto p = leer(null);
            Validador.producto(p);
            ProductoService.registrar(p);
            WindowManager.info("Producto registrado");
            refrescar();
            nuevo();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void actualizar() {
        try {
            Producto sel = seleccionado(), p = leer(sel.id());
            Validador.producto(p);
            try {
                ProductoService.actualizar(p, sel.cantidad());
            } catch (AppException e) {
                // H-27: solo se recarga (y se pisa lo tecleado) si la fila ya no vale: stock cambiado (H-23)
                // o producto borrado. Otros errores (FK, permisos, BD) dejan el formulario para corregir.
                // ponytail: se decide por el texto del mensaje de ProductoRepository; subtipo de AppException si cambia.
                String m = e.getMessage();
                if (m != null && (m.startsWith("El stock de ") || m.equals("Producto no encontrado"))) {
                    try {
                        recargar();
                    } catch (AppException r) { // un solo mensaje, no dos dialogos seguidos
                        throw new AppException(m + System.lineSeparator() + "No se pudo recargar la tabla: " + r.getMessage(), e);
                    }
                }
                throw e;
            }
            WindowManager.info("Producto actualizado");
            refrescar();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void eliminar() {
        try {
            Producto sel = seleccionado();
            if (!WindowManager.confirmar("¿Eliminar el producto \"" + sel.nombre() + "\"?")) return;
            ProductoService.eliminar(sel.id());
            WindowManager.info("Producto eliminado");
            refrescar();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    private Producto seleccionado() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) throw new AppException("Seleccione un producto de la tabla");
        return p;
    }
}
