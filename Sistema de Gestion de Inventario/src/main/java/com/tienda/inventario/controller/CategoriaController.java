package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Categoria;
import com.tienda.inventario.service.CategoriaService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class CategoriaController {
    @FXML private TextField nombre, tipo, umbral;

    private Runnable alGuardar = () -> {};

    public void setAlGuardar(Runnable r) { alGuardar = r; }

    @FXML
    private void guardar() {
        try {
            Categoria c = new Categoria(null, nombre.getText(), tipo.getText(), null, Validador.entero(umbral.getText(), "Umbral"));
            Validador.categoria(c);
            CategoriaService.registrar(c);
            WindowManager.info("Categoría registrada");
            alGuardar.run();
            WindowManager.cerrar(nombre);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
