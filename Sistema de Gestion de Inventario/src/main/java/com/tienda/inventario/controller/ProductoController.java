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
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

public class ProductoController {
    @FXML private TextField nombre, cantidad, precio;
    @FXML private DatePicker expiracion;
    @FXML private ComboBox<Distribuidora> distribuidora;
    @FXML private ComboBox<Categoria> categoria;

    private Runnable alCambiar = () -> {};

    public void setAlCambiar(Runnable r) { alCambiar = r; }

    @FXML private void initialize() {
        WindowManager.formatoFecha(expiracion);
        WindowManager.promptGris(distribuidora, categoria);
    }

    public void refrescar() {
        try {
            distribuidora.getItems().setAll(DistribuidoraService.listar());
            categoria.getItems().setAll(CategoriaService.listar());
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void guardar() {
        try {
            Producto p = new Producto(null, nombre.getText(),
                    distribuidora.getValue() == null ? 0 : distribuidora.getValue().id(),
                    categoria.getValue() == null ? 0 : categoria.getValue().id(),
                    Validador.entero(cantidad.getText(), "Cantidad"), Validador.decimal(precio.getText(), "Precio"),
                    null, null, expiracion.getValue());
            Validador.producto(p);
            ProductoService.registrar(p);
            WindowManager.info("Producto registrado");
            limpiar();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void nuevaCategoria() {
        try {
            CategoriaController c = WindowManager.abrir("categoria.fxml", "Nueva categoría");
            if (c != null) c.setAlGuardar(this::refrescar);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    private void limpiar() {
        nombre.clear();
        cantidad.clear();
        precio.clear();
        expiracion.setValue(null);
    }
}
