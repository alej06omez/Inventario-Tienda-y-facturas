package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.auth.Sesion;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.model.Usuario;
import com.tienda.inventario.service.AuthService;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegistroController {
    @FXML private TextField nombre, username, telefono, respuesta;
    @FXML private ComboBox<String> pregunta;
    @FXML private PasswordField password;
    @FXML private Label rolLabel;
    @FXML private ComboBox<Rol> rol;

    @FXML
    private void initialize() {
        // Cosmetico: la regla real (R6) esta en AuthService.registrar.
        boolean admin = Sesion.actual() != null && Sesion.actual().rol() == Rol.ADMINISTRADOR;
        pregunta.getItems().setAll(AuthService.PREGUNTAS);
        rol.getItems().setAll(Rol.values());
        rol.setValue(Rol.CAJERO);
        WindowManager.promptGris(pregunta, rol);
        for (var n : new javafx.scene.Node[]{rol, rolLabel}) {
            n.setVisible(admin);
            n.setManaged(admin);
        }
    }

    @FXML
    private void guardar() {
        try {
            Usuario u = new Usuario(null, nombre.getText(), username.getText(), password.getText(), telefono.getText(),
                    pregunta.getValue(), respuesta.getText(), rol.getValue());
            Validador.usuario(u);
            AuthService.registrar(u);
            WindowManager.info("Usuario registrado");
            WindowManager.cerrar(nombre);
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
