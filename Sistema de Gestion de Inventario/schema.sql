-- Sistema de Gestion de Inventario - esquema MySQL 8
-- Ejecutar:  mysql -u <usuario> -p < schema.sql
-- Copo de nieve: hechos = venta / producto_vendido; dimensiones = cajero, producto -> categoria,
--                producto -> distribuidora -> distribuidor.

CREATE DATABASE IF NOT EXISTS inventario_tienda CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE inventario_tienda;

CREATE TABLE IF NOT EXISTS usuario (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    nombre                 VARCHAR(100) NOT NULL,
    username               VARCHAR(50)  NOT NULL UNIQUE,
    password               CHAR(60)     NOT NULL,              -- BCrypt
    telefono               VARCHAR(20)  NOT NULL,
    pregunta_recover       VARCHAR(200) NOT NULL,
    respuesta_recover      CHAR(60)     NOT NULL,              -- BCrypt de trim().toLowerCase()
    rol                    ENUM('ADMINISTRADOR','DUENO','CAJERO') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cajero (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    apellido  VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS distribuidor (
    cedula    VARCHAR(20)  PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NULL,
    email     VARCHAR(120) NULL,
    telefono  VARCHAR(20)  NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS distribuidora (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(120) NOT NULL,
    telefono            VARCHAR(20)  NULL,
    email               VARCHAR(120) NULL,
    distribuidor_id     VARCHAR(20)  NULL,
    CONSTRAINT fk_distribuidora_distribuidor FOREIGN KEY (distribuidor_id)
        REFERENCES distribuidor(cedula) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_distribuidora_distribuidor (distribuidor_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS categoria (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(80)  NOT NULL UNIQUE,
    tipo          VARCHAR(80)  NOT NULL,
    registro      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    umbral_stock  INT          NOT NULL DEFAULT 10,          -- {Cantidad configurada por categoria}
    CONSTRAINT ck_categoria_umbral CHECK (umbral_stock >= 1)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS producto (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    nombre               VARCHAR(120)   NOT NULL,
    distribuidora_id     INT            NOT NULL,
    categoria_id         INT            NOT NULL,
    cantidad             INT            NOT NULL,
    precio               DECIMAL(12,2)  NOT NULL,
    fecha_registro       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    fecha_expiracion     DATE           NOT NULL,
    CONSTRAINT ck_producto_cantidad CHECK (cantidad >= 0),
    CONSTRAINT ck_producto_precio   CHECK (precio >= 0),
    CONSTRAINT fk_producto_distribuidora FOREIGN KEY (distribuidora_id) REFERENCES distribuidora(id),
    CONSTRAINT fk_producto_categoria     FOREIGN KEY (categoria_id)     REFERENCES categoria(id),
    INDEX idx_producto_categoria (categoria_id),
    INDEX idx_producto_distribuidora (distribuidora_id),
    INDEX idx_producto_expiracion (fecha_expiracion),
    INDEX idx_producto_cantidad (cantidad)
) ENGINE=InnoDB;

-- Hecho (cabecera): una venta por transaccion de caja.
CREATE TABLE IF NOT EXISTS venta (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    cajero_id  INT           NOT NULL,
    fecha      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total      DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_venta_cajero FOREIGN KEY (cajero_id) REFERENCES cajero(id),
    INDEX idx_venta_cajero (cajero_id),
    INDEX idx_venta_fecha (fecha)
) ENGINE=InnoDB;

-- Hecho (linea): N lineas por venta, con snapshot de nombre y precio.
CREATE TABLE IF NOT EXISTS producto_vendido (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    venta_id         INT           NOT NULL,
    producto_id      INT           NULL,                       -- NULL si luego se elimina el producto; el snapshot conserva el historico
    nombre_snapshot  VARCHAR(120)  NOT NULL,
    precio_snapshot  DECIMAL(12,2) NOT NULL,
    cantidad         INT           NOT NULL,
    CONSTRAINT ck_pv_cantidad CHECK (cantidad > 0),
    CONSTRAINT fk_pv_venta    FOREIGN KEY (venta_id)    REFERENCES venta(id)    ON DELETE CASCADE,
    CONSTRAINT fk_pv_producto FOREIGN KEY (producto_id) REFERENCES producto(id) ON DELETE SET NULL,
    INDEX idx_pv_venta (venta_id),
    INDEX idx_pv_producto (producto_id)
) ENGINE=InnoDB;

-- Seed: categorias base (opcional)
INSERT IGNORE INTO categoria (nombre, tipo, umbral_stock) VALUES
    ('Bebidas',   'Consumo',  10),
    ('Lacteos',   'Perecedero', 10),
    ('Aseo',      'Hogar',    10),
    ('Snacks',    'Consumo',  10);

-- Seed: administrador inicial. username=admin  password=Admin123*
-- pregunta: "¿Cuál es el nombre de su tienda?" (de AuthService.PREGUNTAS, H-17)  respuesta: tienda   (CAMBIAR tras el primer ingreso)
INSERT IGNORE INTO usuario (nombre, username, password, telefono, pregunta_recover, respuesta_recover, rol) VALUES
    ('Administrador', 'admin',
     '$2a$12$qUu0Au/VaGul3fUEeY54Fu8CVz80Hb8PZz//CesBQCQ5J6gfz/YBe',
     '0000000000', '¿Cuál es el nombre de su tienda?',
     '$2a$12$hW/YGVLDYXac.LejzuoE.eRwT8zkDhFVHWVhekI5eZPUM4ad5yin.',
     'ADMINISTRADOR');

-- Migracion iter 3 (H-17): BDs creadas antes conservan la pregunta vieja del seed (INSERT IGNORE no la cambia).
UPDATE usuario SET pregunta_recover = '¿Cuál es el nombre de su tienda?' WHERE pregunta_recover = 'Nombre de la tienda?';
