package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.Distribuidor;
import com.tienda.inventario.model.Distribuidora;
import com.tienda.inventario.service.DistribuidoraService;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.util.List;

public class DistribuidorController {
    @FXML private TextField nombre, telefono, email, cedula, drNombre, drApellidos, drEmail, drTelefono;

    @FXML
    private void guardar() {
        try {
            // Strings vacios -> null y distribuidor vacio -> null: lo hace DistribuidoraService antes de validar.
            DistribuidoraService.registrar(new Distribuidora(null, nombre.getText(), telefono.getText(), email.getText(), null),
                    new Distribuidor(cedula.getText(), drNombre.getText(), drApellidos.getText(), drEmail.getText(), drTelefono.getText()));
            WindowManager.info("Distribuidora registrada");
            List.of(nombre, telefono, email, cedula, drNombre, drApellidos, drEmail, drTelefono).forEach(TextField::clear);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
