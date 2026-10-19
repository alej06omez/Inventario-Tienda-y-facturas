package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.auth.Sesion;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.service.AuthService;
import com.tienda.inventario.view.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.Map;

public class PrincipalController {
    @FXML private TabPane tabs;
    @FXML private Tab tabVenta, tabProducto, tabDistribuidor, tabInventario, tabConfiguracion, tabCerrar;
    @FXML private VentaController ventaController;
    @FXML private ProductoController productoController;
    @FXML private InventarioController inventarioController;
    @FXML private ConfiguracionController configuracionController;
    @FXML private DashboardController dashboardController;

    private Map<Tab, Runnable> refrescos;

    @FXML
    private void initialize() {
        // Cosmetico: los servicios rechazan igual al CAJERO.
        boolean cajero = Sesion.actual() != null && Sesion.actual().rol() == Rol.CAJERO;
        tabProducto.setDisable(cajero);
        tabDistribuidor.setDisable(cajero);
        tabInventario.setDisable(cajero);

        Runnable dash = dashboardController::refrescar;
        ventaController.setAlCambiar(dash);
        productoController.setAlCambiar(dash);
        inventarioController.setAlCambiar(dash);
        configuracionController.setAlCambiar(dash);
        refrescos = Map.of(tabVenta, ventaController::refrescar, tabProducto, productoController::refrescar,
                tabInventario, inventarioController::refrescar, tabConfiguracion, configuracionController::refrescar);

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, antes, tab) -> seleccionar(tab));
        ventaController.refrescar();
        dashboardController.refrescar();
    }

    private void seleccionar(Tab tab) {
        if (tab == tabCerrar) {
            AuthService.logout();
            WindowManager.cerrarTodas();
            try {
                WindowManager.abrir("login.fxml", "Inicio de sesión");
            } catch (AppException e) {
                WindowManager.error(e.getMessage());
            }
            return;
        }
        refrescos.getOrDefault(tab, () -> {}).run();
        dashboardController.refrescar();
    }
}
