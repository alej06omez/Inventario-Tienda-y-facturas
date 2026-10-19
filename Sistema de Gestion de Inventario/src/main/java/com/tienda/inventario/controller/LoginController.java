package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.service.AuthService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {
    @FXML private TextField username;
    @FXML private PasswordField password;

    @FXML
    private void ingresar() {
        try {
            Validador.login(username.getText(), password.getText());
            AuthService.login(username.getText(), password.getText());
            password.clear();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
            return;
        }
        // Ventanas previas (registro sin combo de rol, etc.) se cierran: siempre hay cupo para la principal (H-07).
        WindowManager.cerrarTodas();
        try {
            if (WindowManager.abrir("principal.fxml", "Sistema de Gestión de Inventario") == null) volverALogin();
        } catch (AppException e) {
            volverALogin();
            WindowManager.error(e.getMessage());
        }
    }

    private static void volverALogin() {
        AuthService.logout();
        WindowManager.abrir("login.fxml", "Inicio de sesión");
    }

    @FXML
    private void registrarse() {
        try {
            WindowManager.abrir("registro.fxml", "Registro de usuario");
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML
    private void recuperar() {
        try {
            WindowManager.abrir("recuperar.fxml", "Recuperar contraseña");
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
