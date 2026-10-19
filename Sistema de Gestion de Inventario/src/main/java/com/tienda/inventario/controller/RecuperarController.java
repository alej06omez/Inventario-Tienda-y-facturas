package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.service.AuthService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RecuperarController {
    @FXML private TextField username, respuesta;
    @FXML private PasswordField nueva;
    @FXML private Label pregunta;

    @FXML
    private void verPregunta() {
        try {
            pregunta.setText(AuthService.preguntaDe(username.getText()));
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void cambiar() {
        try {
            Validador.recuperacion(username.getText(), respuesta.getText(), nueva.getText());
            AuthService.recuperar(username.getText(), respuesta.getText(), nueva.getText());
            WindowManager.info("Contraseña actualizada");
            WindowManager.cerrar(username);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
