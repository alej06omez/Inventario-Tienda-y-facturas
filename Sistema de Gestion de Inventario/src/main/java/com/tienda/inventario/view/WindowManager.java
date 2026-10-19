package com.tienda.inventario.view;

import com.tienda.inventario.AppException;
import com.tienda.inventario.service.ConfiguracionService;
import com.tienda.inventario.service.ConfiguracionService.TamanoLetra;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.LocalDateStringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Unico punto que crea Stage (limite de ventanas) y muestra alertas. */
public final class WindowManager {
    public static final int MAX = 4;
    private static final String CSS = WindowManager.class.getResource("/com/tienda/inventario/view/app.css").toExternalForm();
    /** fxml -> ventana abierta (una por fxml: abrir de nuevo la trae al frente sin gastar cupo). */
    private static final Map<String, Stage> abiertas = new LinkedHashMap<>();

    private WindowManager() {}

    /** Devuelve el controlador (el de la ventana ya abierta si existe), o null si ya hay MAX ventanas abiertas. */
    public static <T> T abrir(String fxml, String titulo) {
        Stage ya = abiertas.get(fxml);
        if (ya != null) {
            ya.setIconified(false);
            ya.toFront();
            ya.requestFocus();
            @SuppressWarnings("unchecked") T c = (T) ya.getUserData();
            return c;
        }
        if (abiertas.size() >= MAX) {
            error("Máximo " + MAX + " ventanas abiertas. Cierre una para continuar.");
            return null;
        }
        FXMLLoader loader = new FXMLLoader(WindowManager.class.getResource("/com/tienda/inventario/view/" + fxml));
        Stage stage = new Stage();
        try {
            stage.setScene(new Scene(loader.load()));
        } catch (IOException e) {
            throw new AppException("No se pudo abrir la ventana " + fxml, e);
        }
        stage.setTitle(titulo);
        stage.setUserData(loader.getController());
        estilo(stage.getScene().getRoot());
        abiertas.put(fxml, stage);
        stage.setOnHidden(e -> abiertas.remove(fxml, stage));
        stage.show();
        stage.setMinWidth(Math.min(stage.getWidth(), 1000));
        stage.setMinHeight(Math.min(stage.getHeight(), 700));
        return loader.getController();
    }

    /** Pesos colombianos para mostrar ("$ 1.500" o "$ 12.990,50"). Solo presentacion. */
    public static String moneda(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        // Pesos enteros sin ",00"; si hay centavos, siempre dos (no "12.990,5").
        f.setMinimumFractionDigits(v.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return f.format(v);
    }

    /** Fechas para mostrar: dd/MM/yyyy. Solo presentacion. */
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String fecha(LocalDate d) { return d == null ? "" : FECHA.format(d); }

    /** DatePicker mostrando dd/MM/yyyy (por defecto usa el formato corto del sistema). */
    public static void formatoFecha(DatePicker p) { p.setConverter(new LocalDateStringConverter(FECHA, FECHA)); }

    /** Prompt ("Seleccione...") en gris (clase "prompt") para no confundirlo con un valor elegido; tambien vuelve tras setValue(null). */
    public static void promptGris(ComboBox<?>... combos) { for (ComboBox<?> c : combos) gris(c); }

    private static <T> void gris(ComboBox<T> c) {
        c.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(T v, boolean vacia) {
                super.updateItem(v, vacia);
                boolean prompt = vacia || v == null;
                setText(prompt ? c.getPromptText() : c.getConverter().toString(v));
                getStyleClass().remove("prompt");
                if (prompt) getStyleClass().add("prompt");
            }
        });
    }

    /** Celda de precio/subtotal: COP alineado a la derecha (clase CSS "numero"). */
    public static <S> void columnaMoneda(TableColumn<S, BigDecimal> c) {
        columna(c, WindowManager::moneda);
        c.getStyleClass().add("numero");
    }

    public static <S> void columnaFecha(TableColumn<S, LocalDate> c) { columna(c, WindowManager::fecha); }

    private static <S, T> void columna(TableColumn<S, T> c, Function<T, String> texto) {
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(T v, boolean vacia) {
                super.updateItem(v, vacia);
                setText(vacia || v == null ? null : texto.apply(v));
            }
        });
    }

    public static void cerrar(Node cualquierNodoDeLaVentana) {
        ((Stage) cualquierNodoDeLaVentana.getScene().getWindow()).close();
    }

    /** Para cerrar sesion: el que llama abre login justo despues; implicitExit apagado para no salir con 0 ventanas. */
    public static void cerrarTodas() {
        Platform.setImplicitExit(false);
        new ArrayList<>(abiertas.values()).forEach(Stage::close);
        Platform.runLater(() -> Platform.setImplicitExit(true));
    }

    public static void aplicarFuente() { abiertas.values().forEach(s -> estilo(s.getScene().getRoot())); }

    /** Hoja unica + clase de tamano de letra en la raiz (no setStyle: no pisa estilos inline). */
    private static void estilo(Parent raiz) {
        if (!raiz.getStylesheets().contains(CSS)) raiz.getStylesheets().add(CSS);
        for (TamanoLetra t : TamanoLetra.values()) raiz.getStyleClass().remove(clase(t));
        raiz.getStyleClass().add(clase(ConfiguracionService.tamano()));
    }

    private static String clase(TamanoLetra t) { return "letra-" + t.name().toLowerCase(Locale.ROOT); }

    /** Si la ventana activa tiene un Label visible id="mensaje" el error va ahi (el mas cercano al foco), junto al formulario; si no, Alert. */
    public static void error(String msg) {
        Label l = mensajeVisible();
        if (l == null) {
            alerta(Alert.AlertType.ERROR, "Error", msg, ButtonType.OK);
            return;
        }
        l.setText(msg);
        ajustarAlto(l.getScene());
        // Se borra cuando el usuario pasa a otro campo (empieza a corregir).
        Scene sc = l.getScene();
        ChangeListener<Node> borrar = new ChangeListener<>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Node> o, Node a, Node n) {
                l.setText("");
                sc.focusOwnerProperty().removeListener(this);
            }
        };
        sc.focusOwnerProperty().addListener(borrar);
    }

    /** Un mensaje largo (wrapText) necesita mas alto que el inicial: crece la ventana lo justo, nunca la encoge. */
    static void ajustarAlto(Scene sc) {
        Window w = sc.getWindow();
        double falta = sc.getRoot().prefHeight(sc.getWidth()) - sc.getHeight();
        if (w != null && falta > 0) w.setHeight(w.getHeight() + falta);
    }

    public static void info(String msg) {
        Label l = mensajeVisible();
        if (l != null) l.setText("");
        alerta(Alert.AlertType.INFORMATION, "Información", msg, ButtonType.OK);
    }

    public static boolean confirmar(String msg) {
        return alerta(Alert.AlertType.CONFIRMATION, "Confirmar", msg, ButtonType.OK, ButtonType.CANCEL)
                .filter(b -> b == ButtonType.OK).isPresent();
    }

    private static Optional<ButtonType> alerta(Alert.AlertType tipo, String titulo, String msg, ButtonType... botones) {
        Alert a = new Alert(tipo, msg, botones);
        a.setTitle(titulo);
        a.setHeaderText(null);
        Window w = activa();
        if (w != null) a.initOwner(w);
        estilo(a.getDialogPane());
        return a.showAndWait();
    }

    private static Window activa() {
        return Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
    }

    private static Label mensajeVisible() {
        Window w = activa();
        if (w == null || w.getScene() == null) return null;
        // Del foco hacia arriba: con dos formularios en una pantalla (Configuracion: umbral y Mi cuenta) gana el que se esta usando.
        Node a = w.getScene().getFocusOwner();
        for (a = a == null ? w.getScene().getRoot() : a; a != null; a = a.getParent())
            for (Node n : a.lookupAll("#mensaje"))
                if (n instanceof Label l && visible(l)) return l;
        return null;
    }

    /** Las pestanas no seleccionadas siguen en el grafo pero invisibles. */
    private static boolean visible(Node n) {
        for (; n != null; n = n.getParent()) if (!n.isVisible()) return false;
        return true;
    }
}
