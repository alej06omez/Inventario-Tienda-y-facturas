package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.auth.Sesion;
import com.tienda.inventario.model.Categoria;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.service.AuthService;
import com.tienda.inventario.service.CategoriaService;
import com.tienda.inventario.service.ConfiguracionService;
import com.tienda.inventario.service.ConfiguracionService.TamanoLetra;
import com.tienda.inventario.validator.Validador;
import com.tienda.inventario.view.WindowManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ConfiguracionController {
    @FXML private RadioButton pequena, mediana, grande;
    @FXML private TableView<Categoria> tabla;
    @FXML private TableColumn<Categoria, String> colNombre, colTipo;
    @FXML private TableColumn<Categoria, Integer> colUmbral;
    @FXML private TextField umbral;
    @FXML private VBox seccionUsuarios;
    @FXML private HBox edicionUmbral;
    @FXML private Label notaUmbral;
    @FXML private PasswordField claveActual, claveNueva, respuestaCuenta;
    @FXML private ComboBox<String> preguntaCuenta;

    private Runnable alCambiar = () -> {};

    public void setAlCambiar(Runnable r) { alCambiar = r; }

    @FXML
    private void initialize() {
        switch (ConfiguracionService.tamano()) {
            case PEQUENA -> pequena.setSelected(true);
            case MEDIANA -> mediana.setSelected(true);
            case GRANDE -> grande.setSelected(true);
        }
        colNombre.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().nombre()));
        colTipo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().tipo()));
        colUmbral.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().umbralStock()));
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, c) -> {
            if (c != null) umbral.setText(String.valueOf(c.umbralStock()));
        });
        // Cosmetico: la regla real (R6) esta en AuthService.registrar.
        Rol rol = Sesion.actual() == null ? null : Sesion.actual().rol();
        mostrar(seccionUsuarios, rol == Rol.ADMINISTRADOR);
        // Cosmetico: CategoriaService.actualizarUmbral exige ADMINISTRADOR o DUENO.
        boolean editaUmbral = rol == Rol.ADMINISTRADOR || rol == Rol.DUENO;
        mostrar(edicionUmbral, editaUmbral);
        mostrar(notaUmbral, editaUmbral);
        preguntaCuenta.getItems().setAll(AuthService.PREGUNTAS);
        WindowManager.promptGris(preguntaCuenta);
    }

    @FXML
    private void guardarCuenta() {
        try {
            Validador.cambioCredenciales(claveActual.getText(), claveNueva.getText(), preguntaCuenta.getValue(), respuestaCuenta.getText());
            AuthService.cambiarCredenciales(claveActual.getText(), claveNueva.getText(), preguntaCuenta.getValue(), respuestaCuenta.getText());
            WindowManager.info("Datos de la cuenta actualizados");
            claveActual.clear();
            claveNueva.clear();
            respuestaCuenta.clear();
            preguntaCuenta.setValue(null);
            claveActual.requestFocus();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    private static void mostrar(javafx.scene.Node n, boolean si) {
        n.setVisible(si);
        n.setManaged(si);
    }

    @FXML
    private void registrarUsuario() {
        try {
            WindowManager.abrir("registro.fxml", "Registro de usuario");
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    public void refrescar() {
        try {
            tabla.getItems().setAll(CategoriaService.listar());
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    @FXML private void letraPequena() { cambiar(TamanoLetra.PEQUENA); }

    @FXML private void letraMediana() { cambiar(TamanoLetra.MEDIANA); }

    @FXML private void letraGrande() { cambiar(TamanoLetra.GRANDE); }

    private void cambiar(TamanoLetra t) {
        ConfiguracionService.setTamano(t);
        WindowManager.aplicarFuente();
    }

    @FXML
    private void guardarUmbral() {
        try {
            Categoria c = tabla.getSelectionModel().getSelectedItem();
            if (c == null) throw new AppException("Seleccione una categoría de la tabla");
            int u = Validador.entero(umbral.getText(), "Umbral");
            Validador.umbral(u);
            CategoriaService.actualizarUmbral(c.id(), u);
            WindowManager.info("Umbral actualizado");
            refrescar();
            alCambiar.run();
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }
}
