package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Cajero;
import com.tienda.inventario.service.CajeroService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class CajeroController {
    @FXML private TextField nombre, apellido;

    private Runnable alGuardar = () -> {};

    public void setAlGuardar(Runnable r) { alGuardar = r; }

    @FXML
    private void guardar() {
        try {
            Cajero c = new Cajero(null, nombre.getText(), apellido.getText());
            Validador.cajero(c);
            CajeroService.registrar(c);
            WindowManager.info("Cajero registrado");
            alGuardar.run();
            WindowManager.cerrar(nombre);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
