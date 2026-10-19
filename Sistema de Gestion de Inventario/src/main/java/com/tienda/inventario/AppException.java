package com.tienda.inventario;

/** Unica excepcion para el usuario: el mensaje se muestra tal cual en la UI. */
public class AppException extends RuntimeException {
    public AppException(String msg) { super(msg); }
    public AppException(String msg, Throwable cause) { super(msg, cause); }
}
