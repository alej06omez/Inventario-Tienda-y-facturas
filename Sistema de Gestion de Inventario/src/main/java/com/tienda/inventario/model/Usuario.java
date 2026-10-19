package com.tienda.inventario.model;

/** password/respuestaRecover: en claro al entrar al servicio, hash hacia el repo, null en sesion. */
public record Usuario(Integer id, String nombre, String username, String password, String telefono,
                      String preguntaRecover, String respuestaRecover, Rol rol) {}
