package com.tienda.inventario;

import com.tienda.inventario.view.WindowManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/** Punto de entrada JavaFX. El Stage primario no se usa: todas las ventanas pasan por WindowManager (limite de 4). */
public class App extends Application {
    @Override
    public void start(Stage ignorado) {
        Platform.setImplicitExit(true);
        // Ultima red del hilo FX: stacktrace a stderr y mensaje legible (las de onAction llegan envueltas).
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            Throwable raiz = e;
            while (raiz != null && !(raiz instanceof AppException)) raiz = raiz.getCause();
            try {
                WindowManager.error(raiz != null ? raiz.getMessage() : "Error inesperado. Intente de nuevo o reinicie la aplicación");
            } catch (RuntimeException ignorada) {}
        });
        WindowManager.abrir("login.fxml", "Inicio de sesión");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
