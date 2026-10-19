package com.tienda.inventario.controller;

import com.tienda.inventario.AppException;
import com.tienda.inventario.model.AlertaProducto;
import com.tienda.inventario.model.Nivel;
import com.tienda.inventario.service.DashboardService;
import com.tienda.inventario.view.WindowManager;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class DashboardController {
    private static final PseudoClass ROJO = PseudoClass.getPseudoClass("rojo");
    private static final PseudoClass AMARILLO = PseudoClass.getPseudoClass("amarillo");

    @FXML private ListView<AlertaProducto> expirar, stock;
    @FXML private Label tituloExpirar, tituloStock;

    @FXML
    private void initialize() {
        // El nivel va en texto ademas del color (no depender solo del color).
        expirar.setCellFactory(lv -> celda(a -> (a.nivel() == Nivel.ROJO ? "VENCIDO" : "POR VENCER") + "  |  "
                + a.categoria() + "  |  " + a.producto().nombre() + (a.nivel() == Nivel.ROJO ? "  -  venció " : "  -  vence ") + WindowManager.fecha(a.producto().fechaExpiracion())));
        stock.setCellFactory(lv -> celda(a -> (a.nivel() == Nivel.ROJO ? "CRÍTICO" : "BAJO") + "  |  "
                + a.categoria() + "  |  " + a.producto().nombre() + "  -  " + a.producto().cantidad() + (a.producto().cantidad() == 1 ? " unidad" : " unidades")));
    }

    public void refrescar() {
        try {
            mostrar(expirar, tituloExpirar, "Próximos a expirar", DashboardService.porExpirar());
            mostrar(stock, tituloStock, "Stock bajo", DashboardService.stockBajo());
        } catch (AppException e) {
            WindowManager.error(e.getMessage());
        }
    }

    /** Agrupa por nivel (ROJO primero) y luego por categoria; orden estable: dentro del grupo queda el orden del servicio. */
    private static void mostrar(ListView<AlertaProducto> lista, Label titulo, String texto, List<AlertaProducto> alertas) {
        lista.getItems().setAll(alertas.stream()
                .sorted(Comparator.comparing((AlertaProducto a) -> a.nivel() != Nivel.ROJO)
                        .thenComparing(AlertaProducto::categoria, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList());
        long rojos = alertas.stream().filter(a -> a.nivel() == Nivel.ROJO).count();
        titulo.setText(texto + " (" + alertas.size() + (rojos > 0 ? ", " + rojos + " urgentes" : "") + ")");
    }

    private static ListCell<AlertaProducto> celda(Function<AlertaProducto, String> texto) {
        ListCell<AlertaProducto> c = new ListCell<>() {
            @Override
            protected void updateItem(AlertaProducto a, boolean empty) {
                super.updateItem(a, empty);
                boolean nada = empty || a == null;
                setText(nada ? null : texto.apply(a));
                pseudoClassStateChanged(ROJO, !nada && a.nivel() == Nivel.ROJO);
                pseudoClassStateChanged(AMARILLO, !nada && a.nivel() == Nivel.AMARILLO);
            }
        };
        c.getStyleClass().add("alerta-celda");
        return c;
    }
}
