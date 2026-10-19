# Arquitectura - Sistema de Gestion de Inventario

Fuente de verdad del diseno. Si el codigo contradice este archivo, el codigo esta mal (o se actualiza aqui primero, por el Engineer).

## 1. Stack

| Pieza | Eleccion | Por que |
|---|---|---|
| Lenguaje | Java 25 (Temurin) | Instalado. `maven.compiler.release=25`. |
| UI | JavaFX 25.0.1 (`javafx-controls`, `javafx-fxml`) via Maven | `mvn javafx:run` con `javafx-maven-plugin 0.0.8`. |
| BD | MySQL 8 + `mysql-connector-j 9.4.0`, JDBC plano | Sin ORM. Siempre `PreparedStatement`. |
| Hash | `org.mindrot:jbcrypt:0.4` (BCrypt, cost 12) | Sal y costo embebidos en el hash (una columna `CHAR(60)`), `checkpw` en tiempo constante, API de 2 lineas. PBKDF2 de stdlib obliga a gestionar sal/iteraciones/encoding a mano (~20 lineas mas y mas superficie de error). |
| Tests | JUnit 5.13.4 + H2 2.3.232 modo MySQL (scope test) | Logica pura (validador, dashboard) e integracion real de repos/servicios contra `schema.sql` en H2 (`IntegracionH2Test`, `DbConfigTest`). Nunca MySQL real. |

**Sin `module-info.java`.** El proyecto es no modular (classpath). `javafx-maven-plugin` pone JavaFX en el module-path automaticamente. Verificado: `mvn javafx:run` abre la ventana con JDK 25. Ventaja: FXML puede reflejar controladores sin `opens`. No agregar `module-info` salvo que se necesite `jlink`.

Ejecutar: ver `README.md` (config de BD fuera del classpath, `schema.sql`, `mvn javafx:run`). Seccion 11 = decisiones de la iteracion 2; seccion 12 = iteracion 3; seccion 13 = iteracion 4, la final (la mas nueva manda si chocan).

## 2. Capas y responsabilidades

```
 Vista (FXML + WindowManager)
   |  eventos de UI
 Controlador (clases *Controller de FXML)  -> solo lee campos, llama Validador, llama Servicio, muestra resultado / navega
   |                        \
 Validador (estructura)     Auth (Sesion: quien esta logueado)
   |                        /
 Servicio  -> TODA regla de negocio: permisos por rol, hash, snapshot de precios, estados del dashboard, limites de intentos
   |
 Repositorio (JDBC, PreparedStatement, transacciones)
   |
 MySQL
```

Reglas duras (el Engineer las revisa cada iteracion):
1. **Controlador** no contiene `if` de negocio ni SQL ni chequeos de rol. Solo: `Validador.x(...)` -> `XService.y(...)` -> pintar resultado / `catch (AppException e)` -> alerta. Puede deshabilitar pestanas segun rol como *cosmetica*, pero la regla real esta en el servicio.
2. **Validador** solo estructura: nulos/vacios, longitudes, formato (email, telefono, numeros, fechas), rangos (`cantidad >= 0`). NUNCA consulta la BD ni la sesion. Funciones puras y estaticas -> testeables.
3. **Servicio** es la unica capa que mira `Sesion` y decide permisos (`Permisos.exigir(...)`). Tambien vuelve a llamar al Validador (defensa en profundidad: el servicio no confia en que el controlador valido).
4. **Repositorio** solo SQL. Nunca concatena entrada en SQL. Cada metodo abre `Db.get()` en try-with-resources.
5. Errores para el usuario: una sola excepcion `com.tienda.inventario.AppException extends RuntimeException` (mensaje legible). `SQLException` se envuelve en el repositorio como `AppException("Error de base de datos", e)`; nunca se muestra el SQL ni el stacktrace en la UI.

## 3. Paquetes

Raiz `com.tienda.inventario` (`src/main/java/com/tienda/inventario/`):

| Paquete | Contenido |
|---|---|
| (raiz) | `App` (JavaFX Application), `Db` (conexiones, ya existe), `AppException` |
| `model` | `record`s inmutables: `Rol`(enum), `Usuario`, `Cajero`, `Distribuidor`, `Distribuidora`, `Categoria`, `Producto`, `LineaVenta`, `AlertaProducto`, `Nivel`(enum) |
| `repository` | `UsuarioRepository`, `CajeroRepository`, `DistribuidoraRepository`, `CategoriaRepository`, `ProductoRepository`, `VentaRepository` |
| `validator` | `Validador` (una clase, metodos estaticos por formulario) |
| `auth` | `Sesion` (usuario actual) |
| `service` | `Permisos`, `AuthService`, `CajeroService`, `DistribuidoraService`, `CategoriaService`, `ProductoService`, `VentaService`, `DashboardService`, `ConfiguracionService` |
| `controller` | un `*Controller` por FXML |
| `view` | `WindowManager` (abre/cierra ventanas, limite 4, tamano de letra) |

FXML en `src/main/resources/com/tienda/inventario/view/*.fxml`. CSS opcional en la misma carpeta (`app.css`) - lo define UX_UI.

Todas las clases de repositorio/servicio/validador son `final` con metodos `static` (sin interfaces, sin inyeccion: una sola implementacion).

## 4. Modelo de datos (copo de nieve)

Archivo: `schema.sql` (raiz del proyecto). BD: `inventario_tienda`.

```
                         +-----------------+
                         |  usuario        |   (login; no participa en el copo)
                         | id PK           |
                         | username UNIQUE |
                         | password BCrypt |
                         | rol ENUM        |
                         +-----------------+

 +-----------+        +==================+        +-------------------------+
 | cajero    |1      N|  venta  (HECHO)  |1      N| producto_vendido (HECHO)|
 | id PK     |<-------| id PK            |<-------| id PK                   |
 | nombre    |        | cajero_id FK     |        | venta_id FK (CASCADE)   |
 | apellido  |        | fecha            |        | producto_id FK NULL     |---+
 +-----------+        | total            |        | nombre_snapshot         |   |
                      +==================+        | precio_snapshot         |   |
                                                  | cantidad (>0)           |   |
                                                  +-------------------------+   |
                                                          (ON DELETE SET NULL)  |
                      +-------------------+  N      1 +------------------+      |
                      | producto          |---------->| categoria        |      |
                      | id PK             |           | id PK            |      |
                      | nombre            |           | nombre UNIQUE    |      |
                      | cantidad (>=0)    |           | tipo, registro   |      |
                      | precio (>=0)      |           | umbral_stock     |      |
                      | fecha_registro    |           +------------------+      |
                      | fecha_actualizacion (auto ON UPDATE)                    |
                      | fecha_expiracion  |<------------------------------------+
                      +-------------------+
                             | N
                             v 1
                      +-------------------+  N     1  +----------------------+
                      | distribuidora     |---------->| distribuidor         |
                      | id PK             |  (NULL ok)| cedula PK (VARCHAR)  |
                      | nombre            |           | nombre, apellidos    |
                      | telefono, email   |           | email, telefono      |
                      | distribuidor_id FK|           +----------------------+
                      +-------------------+
```

Decisiones del modelo:
- **Venta tiene N lineas.** La spec dice `venta(producto_vendido_id)`; se invirtio: `producto_vendido.venta_id`. Un FK en venta solo permite 1 producto por venta.
- **Snapshot**: `nombre_snapshot` y `precio_snapshot` los lee el **repositorio desde la BD dentro de la transaccion**, nunca de la UI (evita manipular precio).
- `producto_vendido.producto_id` es `NULL`-able con `ON DELETE SET NULL`: eliminar un producto del inventario no borra el historico de ventas.
- `cajero` es tabla aparte de `usuario` (la spec los separa). Venta referencia `cajero.id`. No se enlaza cajero con usuario en esta iteracion.
- `categoria.umbral_stock` (default 10, CHECK >= 1) = `{Cantidad configurada por categoria}`. Sin tabla extra.
- **Tamano de letra** NO va en BD: es preferencia de la maquina -> `java.util.prefs.Preferences` (stdlib). Valores: `PEQUENA=12px`, `MEDIANA=14px` (default), `GRANDE=18px`.
- `distribuidor.cedula` es PK natural y SI se digita (la spec "no pide identificador" se refiere a los IDs autoincrementales). Distribuidor es opcional en el formulario.
- CHECKs en BD (`cantidad >= 0`, `precio >= 0`, `cantidad > 0` en lineas) son la ultima red contra stock negativo.
- Roles en BD: `ADMINISTRADOR`, `DUENO`, `CAJERO` (sin enie en el identificador; la UI muestra "Dueno"/"Dueño").

Seed: 4 categorias y usuario `admin` / `Admin123*` (pregunta "¿Cuál es el nombre de su tienda?" -de `AuthService.PREGUNTAS`-, respuesta `tienda`; hashes verificados con jbcrypt en iter 3). Cambiar tras el primer ingreso. `schema.sql` termina con un `UPDATE` idempotente que migra la pregunta vieja "Nombre de la tienda?" en BDs ya creadas.

## 5. Permisos por rol (aplicados en `service`)

| Accion | ADMINISTRADOR | DUENO | CAJERO |
|---|:-:|:-:|:-:|
| Login, recuperar password | si | si | si |
| Auto-registro desde login | rol forzado a CAJERO | - | - |
| Registrar usuario con rol elegido | si (cualquier rol) | no | no |
| Registrar venta | si | si | si |
| Registrar cajero | si | si | no |
| Registrar distribuidora/distribuidor | si | si | no |
| Registrar categoria / producto | si | si | no |
| Inventario: actualizar / eliminar producto | si | si | no |
| Listar productos, distribuidoras, cajeros, ver dashboard | si | si | si |
| Configurar umbral por categoria | si | si | no |
| Cambiar tamano de letra | si | si | si (preferencia local) |
| Mi cuenta: cambiar su contraseña, pregunta y respuesta (pide la actual) | si | si | si |

`Permisos.exigir(Rol... permitidos)` lanza `AppException("No tiene permisos para esta accion")` si no hay sesion o el rol no esta en la lista. Se llama en la PRIMERA linea de cada metodo de servicio que lo requiera.

## 6. Sesion y ventanas

- `Sesion` guarda el `Usuario` logueado en un campo estatico (app de escritorio, un usuario a la vez). `Sesion.actual()` / `Sesion.iniciar(u)` / `Sesion.cerrar()`. El `Usuario` en sesion lleva `password` y `respuestaRecover` en `null` (no mantener hashes en memoria).
- Flujo: `App.start` -> `WindowManager.abrir("login.fxml")`. Login ok -> `Sesion.iniciar` -> abrir `principal.fxml` -> cerrar login. Pestana "Cerrar sesion" -> `Sesion.cerrar()` -> `WindowManager.cerrarTodas()` -> abrir login.
- Cualquier controlador de pantalla protegida (todas menos login/registro/recuperar) falla cerrado: si `Sesion.actual()==null`, el servicio lanza `AppException` igual.
- **Limite de 4 ventanas**: `WindowManager` mantiene `List<Stage> abiertas`; `abrir()` devuelve `null` y muestra alerta "Maximo 4 ventanas abiertas" si `abiertas.size() >= 4`. Cada `Stage` se quita de la lista en `setOnHidden`. Cuenta TODAS las ventanas propias (login, principal, dialogos de cajero/categoria/registro); las `Alert` no cuentan.
- El tamano de letra se aplica con `scene.getRoot().setStyle("-fx-font-size: Npx")` en cada `abrir()` y, al cambiarlo, a todas las ventanas abiertas.

## 7. Pantallas

| FXML | Controlador | Notas |
|---|---|---|
| `login.fxml` | `LoginController` | username, password, botones Ingresar / Registrarse / Olvide mi contrasena |
| `registro.fxml` | `RegistroController` | todos los campos de usuario; combo rol visible solo si sesion es ADMINISTRADOR |
| `recuperar.fxml` | `RecuperarController` | username -> muestra pregunta -> respuesta + nueva password |
| `principal.fxml` | `PrincipalController` | `TabPane` [Venta, Producto, Distribuidor, Inventario, Configuracion, Cerrar sesion] con `fx:include` por pestana; debajo `fx:include` del dashboard |
| `venta.fxml` | `VentaController` | combo cajero, combo producto + cantidad + "Agregar linea", tabla de lineas, total, "Registrar venta", boton "Nuevo cajero" |
| `producto.fxml` | `ProductoController` | campos de producto, combos distribuidora y categoria, boton "Nueva categoria" |
| `distribuidor.fxml` | `DistribuidorController` | distribuidora + distribuidor en un formulario |
| `inventario.fxml` | `InventarioController` | `TableView` de productos, editar seleccionado, eliminar con confirmacion |
| `configuracion.fxml` | `ConfiguracionController` | 3 radio de tamano de letra; tabla categoria + umbral editable; Usuarios (solo ADMIN); Mi cuenta (todos, 13.7) |
| `dashboard.fxml` | `DashboardController` | dos listas: proximos a expirar, stock bajo, con color por `Nivel` |
| `cajero.fxml` | `CajeroController` | ventana aparte (cuenta para el limite) |
| `categoria.fxml` | `CategoriaController` | ventana aparte; al guardar, el combo de producto se recarga |

"Menu inicial" = la ventana principal con pestanas; cada pestana es el formulario de registro de su objeto.

## 8. Reglas del dashboard (en `DashboardService`, puro y testeable)

**Interpretacion documentada:** la spec dice "una semana antes de que se cumpla la *fecha de actualizacion*". Se usa `fecha_expiracion`, porque "expirar" solo tiene sentido contra esa fecha (`fecha_actualizacion` es pasada por definicion).

- Proximos a expirar (`hoy` = `LocalDate.now()`):
  - `ROJO` si `fecha_expiracion < hoy` (ya expirado).
  - `AMARILLO` si `hoy <= fecha_expiracion <= hoy + 7 dias`.
  - Otros: no se listan.
- Stock bajo (umbral = `categoria.umbral_stock`):
  - `ROJO` si `cantidad <= 1` (independiente del umbral).
  - `AMARILLO` si `1 < cantidad < umbral`.
  - Otros: no se listan.
- Orden: ROJO primero, luego por fecha/cantidad ascendente. Agrupado/mostrando categoria.
- Se refresca al abrir la principal, al cambiar de pestana y tras registrar venta/producto/umbral.

Las funciones `nivelExpiracion(LocalDate exp, LocalDate hoy)` y `nivelStock(int cantidad, int umbral)` son estaticas y puras -> test JUnit obligatorio.

## 9. Venta: transaccion y concurrencia

`VentaRepository.registrar(int cajeroId, List<LineaVenta> lineas)` en UNA transaccion (`setAutoCommit(false)`):
1. Lineas ya fusionadas por producto y **ordenadas por `productoId`** (lo hace `VentaService`) -> orden de bloqueo fijo, evita deadlocks entre dos cajas.
2. Por linea: `UPDATE producto SET cantidad = cantidad - ? WHERE id = ? AND cantidad >= ?`. Si `executeUpdate() == 0` -> `rollback` y `AppException("Stock insuficiente para el producto " + id)`. Esto es atomico: no existe ventana leer-luego-escribir.
3. Por linea: `SELECT nombre, precio FROM producto WHERE id = ?` (la fila ya esta bloqueada por el UPDATE) -> snapshot y subtotal con `BigDecimal`.
4. `INSERT venta(cajero_id, total)` con `RETURN_GENERATED_KEYS`, luego `INSERT producto_vendido` por linea (`addBatch`).
5. `commit`. Cualquier excepcion -> `rollback` y relanzar como `AppException`.

(El total se calcula tras los UPDATE y antes de insertar la venta; insertar venta con total y luego lineas.)

## 10. Riesgos y vulnerabilidades previstas (el Tester debe atacarlos)

| # | Riesgo | Mitigacion obligatoria |
|---|---|---|
| R1 | SQL injection | Solo `PreparedStatement` con `?`. Prohibido `Statement` y concatenar strings en SQL. Orden de columnas (si se agrega) desde whitelist fija. |
| R2 | Passwords / respuestas en claro | BCrypt cost 12 para `password` y `respuesta_recover`. Respuesta normalizada `trim().toLowerCase()` antes de hashear y de comparar. Nunca loguear passwords. |
| R3 | Enumeracion de usuarios en login | Mensaje unico "Usuario o contrasena incorrectos". Si el usuario no existe igual ejecutar `BCrypt.checkpw` contra un hash dummy (tiempo similar). |
| R4 | Enumeracion en recuperacion | Preguntas de una lista cerrada `AuthService.PREGUNTAS` (registro con ComboBox, el servicio rechaza otras). Inexistente -> `PREGUNTAS[floorMod(canon(username).hashCode(), N)]`, `canon` = NFKD + sin marcas/formato/controles + strip + `Locale.ROOT` + `ß`->`ss` (13.1). Mismo mensaje generico al fallar. |
| R5 | Fuerza bruta (login y respuesta) | Contador en memoria por identidad de BD: 5 fallos dentro de 5 min -> bloqueo 5 min; el contador decae y los mapas se purgan (12.3, 13.2). `// ponytail: en memoria, se pierde al reiniciar; mover a columna en BD si hace falta.` |
| R6 | Escalada de rol en auto-registro | `AuthService.registrar` fuerza `CAJERO` salvo que `Sesion` sea ADMINISTRADOR. El rol de la UI no se confia. |
| R7 | Stock negativo / venta concurrente | Ver seccion 9: `UPDATE ... WHERE cantidad >= ?` + transaccion + CHECK en BD. |
| R8 | Manipulacion de precio en venta | Precio y nombre se leen de BD en la transaccion; la UI solo manda `productoId` y `cantidad`. |
| R9 | Credenciales BD | Config externa (`-Ddb.config` > `~/.inventario/db.properties` > classpath), ver 11.1. `db.properties` excluido del jar. Usuario MySQL dedicado con permisos solo sobre `inventario_tienda`; sin `?user=` en la URL. |
| R10 | Fuga de errores | La UI muestra `AppException.getMessage()`; nunca `SQLException` ni stacktrace. |
| R11 | Bypass de permisos llamando servicios | Permisos en servicio (no en controlador). Controlador solo oculta/deshabilita. |
| R12 | Dinero en `double` | `BigDecimal` en modelo y `DECIMAL` en BD; `setBigDecimal`/`getBigDecimal`. |
| R13 | Mas de 4 ventanas | Contador central en `WindowManager`, unico punto que crea `Stage`. |
| R14 | Borrar producto con ventas | `ON DELETE SET NULL` + snapshot. Borrar categoria/distribuidora con productos -> FK lo impide; envolver en `AppException` legible. |
| R15 | Entrada enorme | Validador limita longitudes a las de las columnas. |
| R16 | Actualizacion perdida de stock (Inventario vs venta simultanea) | `UPDATE producto ... WHERE id = ? AND cantidad = ?` con la cantidad leida al cargar el formulario (12.6). |

## 11. Decisiones - Iteracion 2

### 11.1 Configuracion de BD (H-02, H-04)
- `Db` carga la config **perezosamente** (primer `Db.get()`), no en `static {}`: un fallo es `AppException` legible, nunca `ExceptionInInitializerError`.
- Orden: `System.getProperty("db.config")` (si se da y no existe -> `AppException`, sin fallback) > `~/.inventario/db.properties` > `/db.properties` del classpath > `AppException("Falta la configuracion de la base de datos (ver README.md)")`.
- `pom.xml`: `maven-jar-plugin` excluye `db.properties` del jar (sigue en `target/classes` para `mvn javafx:run`); surefire fija `db.config` al H2 de `src/test/resources` (los tests nunca leen la config real del usuario).
- `src/main/resources/db.properties` pertenece al USUARIO: nadie lo edita ni lo borra.
- Timeouts (H-08): `connectTimeout=5000&socketTimeout=15000` en la URL (README/example). `// ponytail: JDBC en hilo FX; la UI se congela hasta 5 s si MySQL cae. Mover login/venta a javafx.concurrent.Task si se nota.`

### 11.2 Manejo global de errores (H-04)
- `App.start` instala `Thread.currentThread().setUncaughtExceptionHandler(...)` en el hilo FX: imprime el stacktrace en stderr y muestra `WindowManager.error(...)` con el mensaje si es `AppException`, si no `"Error inesperado. Intente de nuevo o reinicie la aplicación"`. El handler se protege con try/catch propio (si mostrar la alerta falla, solo stderr).
- `AuthService` compara hashes con un helper privado `coincide(plano, hash)` que devuelve `false` ante `IllegalArgumentException` de jbcrypt (hash corrupto en BD).

### 11.3 Bloqueo por intentos (H-01, H-03) - ver 12.1 y 12.3 (iter 3 reemplaza `PREGUNTAS_FALSAS` y la normalizacion)
- La clave del contador es **la identidad que devuelve la BD**: `"id:" + usuario.id()` si existe; si no, `"u:" + username.strip().toLowerCase(Locale.ROOT)`. Asi "víctima"/"VICTIMA"/"victima" (iguales para `utf8mb4_unicode_ci`) comparten contador. Orden en `login`/`recuperar`: buscar usuario -> clave -> `verificarBloqueo` -> `coincide` -> fallo/limpiar.
- Prefijos separados `login:` y `rec:` como hoy. Recuperar con exito limpia ambos de ese id.
- `preguntaDe(username)`: si el usuario no existe devuelve una pregunta **plausible y estable** `PREGUNTAS_FALSAS[Math.floorMod(normalizado.hashCode(), N)]` (lista fija de 5, sin "Nombre de la tienda?"). Si la clave `rec:` esta bloqueada lanza el mismo mensaje de bloqueo. `// ponytail: una pregunta propia muy rara sigue siendo distinguible; ceiling aceptado.`

### 11.4 Distribuidor con cedula existente (H-05)
- `DistribuidoraRepository.registrar`: `INSERT IGNORE` -> `SELECT ... WHERE cedula = ?` en la misma transaccion.
  - No existe -> `INSERT` normal.
  - Existe y **cada campo no nulo enviado** coincide (`equalsIgnoreCase` tras strip) -> se reutiliza.
  - Existe con algun dato distinto -> `rollback` + `AppException("Ya existe un distribuidor con cédula X a nombre de NOMBRE APELLIDOS. Use los mismos datos o deje vacíos los campos del distribuidor")`.
- La comparacion vive en el repositorio porque necesita la fila dentro de la transaccion; es una regla de integridad de datos, no de permisos.

### 11.5 Gestion de usuarios por ADMIN (H-06)
- `configuracion.fxml` gana seccion "Usuarios" con boton "Registrar usuario" visible **solo** si `Sesion.actual().rol()==ADMINISTRADOR` (cosmetico, en `ConfiguracionController.initialize`). Abre `registro.fxml`, donde ya se muestra el combo de rol para ADMIN. La regla real sigue en `AuthService.registrar` (R6).
- Login exitoso hace `WindowManager.cerrarTodas()` y luego abre `principal.fxml` (H-07): siempre hay cupo, y una ventana de registro abierta antes del login (sin combo de rol) no se reutiliza ya logueado. Si `abrir` aun devolviera `null`, `AuthService.logout()`.
- Efecto colateral buscado: los errores de `PrincipalController.initialize` ya no caen en el label de la ventana de login que se cierra (no hay ventana enfocada -> `Alert`).

### 11.6 Inventario "registrar" (H-14, spec linea 31)
- `inventario.fxml`: botones "Nuevo" (limpia seleccion y campos) y "Registrar" (crea producto con los campos; id null). Reusa `ProductoService.registrar`. El armado del `Producto` desde los campos va en un helper privado del controlador compartido por actualizar/registrar.

### 11.7 Mensajes al usuario
- Todos los mensajes visibles (`AppException`, `WindowManager.info`) en espanol con tildes y enie: "contraseña", "categoría", "recuperación", "inválido", "número", "sesión", "teléfono", "cédula", "expiración", "líneas", "máx.". Identificadores Java/BD siguen sin tildes. Los tests que comparan mensajes exactos se actualizan en el mismo cambio.
- Stock (H-10): si el `UPDATE` afecta 0 filas, `SELECT nombre, cantidad` en la transaccion: sin fila -> `"Producto no encontrado (id N)"`; con fila -> `"Stock insuficiente para \"NOMBRE\" (disponible: N)"`. Prefijo `Stock insuficiente` se conserva.
- Rollback (H-15): `try { c.rollback(); } catch (SQLException r) { e.addSuppressed(r); }` antes de relanzar, en `VentaRepository` y `DistribuidoraRepository`.
- Moneda: pesos colombianos (la BD usa `America/Bogota`). Solo presentacion: `NumberFormat.getCurrencyInstance(Locale.of("es", "CO"))` en celdas/labels; el modelo sigue en `BigDecimal`. Lo hace UX_UI (helper `WindowManager.moneda(BigDecimal)`).

### 11.8 Validador (H-09) y categoria (H-13)
- Telefono: `^(?=(?:\D*\d){7})[0-9+ -]{7,20}$` (al menos 7 digitos).
- `CategoriaService.registrar`: se borra la linea muerta del default 0->10 (el FXML ya trae `10` prellenado y el umbral es obligatorio).

### 11.9 Revision de capas iteracion 2 (Engineer)
- Repositorios: 100% `PreparedStatement`, sin concatenacion de entrada (grep OK).
- Validador: sin BD ni Sesion. OK.
- Servicios: `Permisos.exigir` en primera linea donde aplica. OK.
- Controladores: solo mapeo UI->modelo, `Validador`, servicio, pintar. Aceptado: total "de referencia" en `VentaController` (solo visual), `"Seleccione ..."` por estado de UI, chequeo de rol cosmetico en `Principal/Registro/Configuracion`.
- UX_UI iter 1 (`app.css`, FXML, `WindowManager` con reuso por fxml y mensaje inline): no rompe capas; la logica nueva es de vista. Unico warning: import sin uso `java.util.List` en `WindowManager.java:21`.

## 12. Decisiones - Iteracion 3

### 12.1 Preguntas de recuperacion cerradas (H-17)
- `AuthService.PREGUNTAS` = `public static final List<String>` con las 5 preguntas de iter 2 **en el mismo orden** + `"¿Cuál es el nombre de su tienda?"` (6). Reemplaza `PREGUNTAS_FALSAS`. Es la UNICA lista: la usa el ComboBox de `registro.fxml` y el calculo de pregunta falsa. Asi la pregunta real de un usuario y la falsa de un inexistente salen del mismo conjunto.
- `AuthService.registrar` rechaza `preguntaRecover().strip()` fuera de la lista: `AppException("Seleccione una pregunta de la lista")`. Va en el servicio (no en `Validador`) para que el validador no dependa de `service`.
- `private static String canon(String username)` = `Normalizer.normalize(username.strip(), Normalizer.Form.NFKD).replaceAll("[\\p{M}\\p{Cf}]", "").toLowerCase(Locale.ROOT)`. Se usa en `clave()` (rama `"u:"`) y en el indice de la pregunta falsa. Quita tildes, formas de ancho completo/ligaduras (NFKD) y caracteres de formato invisibles (U+200B, U+00AD...) que `utf8mb4_unicode_ci` ignora.
- Ceilings aceptados (`// ponytail:`): (a) usuarios creados antes de iter 3 con pregunta libre siguen distinguibles (migrarlos a mano); (b) equivalencias de collation que NFKD no cubre (p. ej. `ß`=`ss`) dan otra pregunta falsa; (c) el auto-registro responde "El username ya existe": oraculo inherente a permitir registro sin sesion. Con (a)-(c) documentados H-17 baja a BAJO.
- `schema.sql`: seed y migracion a `"¿Cuál es el nombre de su tienda?"`; respuesta `tienda` sin cambio (hash verificado). README: `mysql --default-character-set=utf8mb4`.

### 12.2 Hashes (H-18, HASH_DUMMY)
- `coincide()` atrapa `IllegalArgumentException | IndexOutOfBoundsException` (jbcrypt 0.4 lanza `StringIndexOutOfBoundsException` con `""`, `"$2a"`, `"$2a$12$corto"`; `NumberFormatException` ya es `IllegalArgumentException`). Verificado por el Engineer con jbcrypt 0.4.
- `HASH_DUMMY` propio (cost 12, de un UUID aleatorio descartado: nadie conoce el texto): `$2a$12$nSGEHqbx4IeJTuAsjcOoyeZixRl0oJvrgYSmlkEcCt1CbiNAzOF6C`. Ya no es el hash del admin seed.

### 12.3 Contadores de bloqueo: decaimiento y purga
- `private record Fallos(int n, Instant desde) {}`; `Map<String, Fallos> fallos`. Un fallo con `desde + BLOQUEO` ya vencido reinicia la cuenta a 1 (ventana de 5 min desde el primer fallo). Al llegar a 5 -> se quita de `fallos` y se pone `bloqueadoHasta`.
- `verificarBloqueo`: bloqueo vencido -> `bloqueadoHasta.remove(key)`.
- Purga: si `fallos.size() > 10_000` tras un fallo -> `removeIf` de entradas vencidas en ambos mapas. Cota natural: cada intento cuesta un BCrypt cost 12 (~0,25 s en hilo FX); en una ventana de 5 min caben ~1.200 claves. `// ponytail: sin limite duro; LRU o BD si algun dia se expone por red.`

### 12.4 Config de BD (H-20 + claves)
- `Path.of(...)` dentro de `try`; `InvalidPathException` -> `AppException("Ruta de configuración inválida: " + ruta, e)`.
- Tras `p.load(in)`: `db.url` y `db.user` obligatorios y no vacios; si falta -> `AppException("Falta " + clave + " en la configuración de la base de datos (ver README.md)")`. `db.password` puede faltar o ir vacia. Una config invalida no se cachea.

### 12.5 Distribuidor (H-19, H-21)
- H-21: vincular un distribuidor existente **solo con la cedula**. `Validador.distribuidora`: con `dr != null` la cedula sigue obligatoria; `dr.nombre()` pasa a opcional (si viene, 1..100). En `DistribuidoraRepository.registrar`, cedula inexistente sin nombre -> `AppException("No existe un distribuidor con cédula X; ingrese su nombre para registrarlo")`. Mensaje H-05 nuevo: `"Ya existe un distribuidor con cédula X a nombre de N A. Use los mismos datos o deje solo la cédula para vincularlo"`. La regla "nuevo distribuidor necesita nombre" vive en el repositorio porque depende de la fila leida en la transaccion (igual que 11.4); `distribuidor.nombre NOT NULL` es la red final.
- H-19: SELECT normal; si el `INSERT INTO distribuidor` lanza `SQLIntegrityConstraintViolationException` (otra caja lo creo entre el SELECT y el INSERT), se repite la comparacion **una vez** con `SELECT ... FOR UPDATE` (lectura bloqueante = ultimo commit en InnoDB y H2; el error de clave duplicada solo revierte la sentencia, no la transaccion). Mismos datos -> reuso; distintos -> mensaje H-05. Nunca "El distribuidor indicado no existe" por esta carrera. Dos literales SQL fijos (con y sin `FOR UPDATE`), sin concatenar.

### 12.6 Stock: actualizacion perdida en Inventario (H-23, hallazgo del Engineer)
- Problema: Inventario carga `cantidad=10`; una caja vende 3 (`cantidad=7`); el DUENO pulsa "Actualizar" (p. ej. para corregir el nombre) y escribe `cantidad=10`: aparecen 3 unidades fantasma.
- Fix: `ProductoService.actualizar(Producto p, int cantidadLeida)` -> `ProductoRepository.actualizar(Producto p, int cantidadLeida)`: `UPDATE ... WHERE id = ? AND cantidad = ?`. 0 filas -> `SELECT nombre, cantidad FROM producto WHERE id = ?`: sin fila -> `"Producto no encontrado"`; con fila -> `"El stock de \"NOMBRE\" cambió a N mientras lo editaba (p. ej. una venta). Revise los datos y vuelva a actualizar"`.
- `InventarioController.actualizar` pasa `seleccionado().cantidad()`; si el **servicio** falla -> `refrescar()` (recarga la fila con el stock real) y muestra el error. Errores del `Validador` no recargan (no se pierde lo tecleado).
- Se compara solo `cantidad` (lo que pisa una venta); `fecha_actualizacion` tiene resolucion de 1 s y no sirve. `// ponytail: los demas campos son last-write-wins entre dos editores; columna version INT si hace falta.`

### 12.7 Revision de capas iteracion 3 (Engineer)
- Repositorios: 100% `PreparedStatement`, sin concatenacion de entrada (grep OK). Validador puro (sin BD/Sesion). Servicios con `Permisos.exigir` en primera linea donde aplica. `Db` unico punto de conexion.
- Controladores: solo UI -> `Validador` -> servicio -> pintar. Excepciones aceptadas (sin cambio): `Sesion` leida en Principal/Registro/Configuracion para ocultar/deshabilitar (cosmetico); `"Seleccione ..."` por estado de UI; total "de referencia" en Venta; `DashboardController` re-agrupa por categoria (presentacion; el nivel lo da el servicio); `DistribuidorController` delega vacio->null al servicio.
- `WindowManager` (view) lee `ConfiguracionService.tamano()`: dependencia vista->servicio aceptada (preferencia local, sin permisos ni BD). Sigue siendo el unico punto que crea `Stage`. `App`: handler global del hilo FX OK.
- Violacion de estilo (UX): `WindowManager.columnaMoneda` usa `setStyle(...)` inline; contradice "sin estilos inline" (H-16) -> clase CSS (U2).
- H-22 verificado: `InventarioController.java:130` y `VentaController.java:89` empiezan con "¿".
- Test defectuoso: `h17PreguntaNoDistingueExistenciaPorVariantes` **inserta** la tilde (`"existeá_pq"` es otro usuario) en vez de sustituir una letra; su primer assert falla aun con el fix (verificado: `IntegracionH2Test.java:573`, 4 preguntas). Se corrige en T1.

## 13. Decisiones - Iteracion 4 (FINAL)

La iteracion 4 es la ultima. Todo hallazgo abierto se cierra aqui con codigo o con una decision documentada; lo unico que puede quedar pendiente es lo que depende del usuario (13.12). Manda sobre 11 y 12 si chocan.

### 13.1 `canon()` cubre lo que MySQL ignora (H-24)
- `canon(username)` = `Normalizer.normalize(username, NFKD).replaceAll("[\\p{M}\\p{Cf}\\p{Cc}]", "").strip().toLowerCase(Locale.ROOT).replace("ß", "ss")`.
- Dos cambios respecto de 12.1: se quitan tambien los controles `\p{Cc}` (U+0001, U+007F...) y el `strip()` va DESPUES de NFKD (NFKD convierte U+00A0, U+2007 y U+202F en espacio normal, y entonces `strip()` los quita igual que el PAD SPACE de MySQL). La sugerencia del Tester se valido con 13 variantes contra MySQL 8.0.44 real.
- `ß`=`ss` se cubre con el `replace`, asi que el ceiling (b) de 12.1 se cierra. Queda solo lo que ni MySQL ni `canon()` igualan (NBSP en medio, `æ`...): no distingue existencia. H-01 no cambia (el bloqueo de usuarios existentes sigue por `id:N`).

### 13.2 Purga de contadores (H-25)
- En `fallo()`: `if (fallos.size() + bloqueadoHasta.size() > MAX_CLAVES) purgar(ahora);`. Con eso los bloqueos vencidos que nadie vuelve a consultar tambien se purgan. Sin mas cambios en 12.3.

### 13.3 Configuracion y conexion de BD (H-26)
- Estado del usuario: el formato de `src/main/resources/db.properties` se reviso sin mostrar valores (solo largo y presencia de caracteres). `db.user` ya no lleva comillas ni `@host`, la clave no va entre comillas, la URL no tiene `??` ni `user=`. La parte de configuracion de H-26 queda cerrada por el usuario. Sigue sin existir `~/.inventario/db.properties` (recomendado, 13.12).
- `Db.props()`, despues de las claves obligatorias:
  - `db.user` con espacios, `'`, `"` o `@` (regex `[\\s'\"@]`) -> `AppException("db.user debe ser solo el nombre del usuario de MySQL, sin comillas, espacios ni @host (ver README.md)")`.
  - `db.url` con `??` -> `AppException("db.url tiene \"??\": use un solo ? antes de los parámetros (ver README.md)")`.
  - `db.url` con `user=` o `password=` como parametro (regex `(?i)[?&;](user|password)=`) -> `AppException("db.url no debe incluir user= ni password=: van en db.user y db.password (ver README.md)")`.
  - Una config invalida sigue sin cachearse. `db.password` no se valida (una clave puede tener comillas legitimas); el README avisa.
- `Db.get()` traduce el fallo de conexion en un solo lugar (todos los repos llaman ahi y `AppException` no es `SQLException`, asi que atraviesa sus `catch`):
  - SQLState que empieza con `28` (credenciales; MySQL `28000`, H2 tambien) -> `AppException("No se pudo conectar a MySQL: usuario o contraseña incorrectos (revise db.user y db.password, sin comillas)", e)`.
  - SQLState que empieza con `08` (sin conexion, p. ej. `08S01` "Communications link failure") -> `AppException("No se pudo conectar a MySQL: revise que el servidor esté encendido y la dirección de db.url", e)`.
  - Otro -> se relanza la `SQLException` (el repo la envuelve como siempre).

### 13.4 Distribuidor concurrente sin deadlock (R-H19)
- Problema: en InnoDB el INSERT que choca por clave duplicada deja un lock S en la fila; despues `SELECT ... FOR UPDATE` pide X. Con 3 o mas cajas a la vez sobre la misma cedula nueva, dos transacciones con S esperan X una de la otra (deadlock 1213).
- `FOR SHARE` descartado: H2 2.3.232 no lo acepta (ni `LOCK IN SHARE MODE`; verificado por el Engineer con el jar de H2), y romperia la suite.
- Decision: `DistribuidoraRepository.registrar` hace `c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)` junto al `setAutoCommit(false)`. En READ COMMITTED una lectura normal ve el ultimo commit, asi que el reintento tras la clave duplicada usa el MISMO `SELECT` sin bloqueo: nadie pide X sobre la fila del distribuidor y no hay ciclo S->X. El lock S que deja el duplicado es compatible con el S que pide la FK del INSERT de `distribuidora`. Se borra el literal `FOR UPDATE` y el parametro `bloquear` de `existente()`. La conexion es nueva por operacion (sin pool), el cambio de aislamiento no se filtra a otras operaciones. H2 ya usa READ COMMITTED por defecto.
- Red final: `SQLTransactionRollbackException` (Connector/J y H2 la usan para el deadlock, SQLState 40001) en `DistribuidoraRepository.registrar` y `VentaRepository.registrar` -> `AppException("Otra operación simultánea chocó con esta y se deshizo. Intente de nuevo", e)`. Se atrapa antes del `catch (SQLException)` generico.
- Verificacion en InnoDB real: depende de los privilegios del usuario (13.12); `MySqlRealTest` ya trae los casos.

### 13.5 Contrato de mensajes de Inventario (H-27 residual)
- Se mantiene la decision de UX_UI: `InventarioController.actualizar` recarga solo si el mensaje empieza con `"El stock de "` o es `"Producto no encontrado"`. No se crea un subtipo de `AppException` (regla 5 de la seccion 2: una sola excepcion).
- El riesgo era que alguien cambie el texto y la recarga deje de ocurrir en silencio. Ya esta cubierto: `h23ActualizarNoPisaVentaIntermedia` y `h23ProductoBorradoEntreCargarYActualizar` comparan esos textos exactos. Se agrega un comentario en `ProductoRepository.actualizar` que nombra la dependencia. H-27 queda CERRADO.

### 13.6 Cedula tal como esta en BD (mejora del Tester)
- `existente()` devuelve la cedula guardada (`String`, `null` = no existe) en vez de `boolean`; el INSERT de `distribuidora` usa ese valor. Asi `m21-1` tecleado se vincula como `M21-1`. Para una cedula nueva se usa la tecleada.
- `igual()` sigue con `equalsIgnoreCase` (sensible a tildes): `Jose` vs `José` da el mensaje H-05, que muestra el nombre guardado para que el usuario lo copie. Aceptado.

### 13.7 "Mi cuenta": cambiar contraseña y recuperacion (H-28, ALTO, hallazgo del Engineer)
- Problema: la respuesta de recuperacion del admin semilla (`tienda`) es publica (`schema.sql`, README) y el README manda a cambiar la clave con "Olvidé mi contraseña", que NO cambia la respuesta. Cualquiera frente al equipo puede resetear la clave del ADMINISTRADOR para siempre. Ademas ningun usuario puede cambiar su clave, pregunta o respuesta estando logueado.
- Fix (capas):
  - Validador `cambioCredenciales(String actual, String nueva, String pregunta, String respuesta)`: `actual` no vacia, `password(nueva)`, `pregunta` 1..200, `respuesta` 1..72 bytes (13.8). Solo estructura.
  - `AuthService.cambiarCredenciales(String actual, String nueva, String pregunta, String respuesta)`: `Permisos.exigir()` (cualquier rol); `Validador.cambioCredenciales(...)`; pregunta fuera de `PREGUNTAS` -> el mismo mensaje de 12.1; relee el usuario de la sesion con `UsuarioRepository.porUsername(Sesion.actual().username())`; clave del contador `"login:id:N"` (comparte bloqueo con el login: adivinar la clave actual desde una sesion abierta cuenta como fallo de login); `verificarBloqueo`; `coincide(actual, hash)` falso -> `fallo` + `AppException("La contraseña actual no es correcta")`; ok -> `UsuarioRepository.actualizarCredenciales(id, hash(nueva), pregunta.strip(), hash(normalizar(respuesta)))` y `limpiar` de `login:` y `rec:`.
  - `UsuarioRepository.actualizarCredenciales(int id, String hashPassword, String pregunta, String hashRespuesta)`: un `UPDATE usuario SET password = ?, pregunta_recover = ?, respuesta_recover = ? WHERE id = ?`.
  - Vista/controlador: seccion "Mi cuenta" en `configuracion.fxml` visible para TODOS los roles (contraseña actual, nueva, pregunta en `ComboBox` con `AuthService.PREGUNTAS`, respuesta en `PasswordField`, boton "Guardar mi cuenta"). `ConfiguracionController.guardarCuenta()`: leer campos -> `Validador.cambioCredenciales` -> `AuthService.cambiarCredenciales` -> `info("Datos de la cuenta actualizados")` -> limpiar los cuatro campos.
- Los cuatro campos son obligatorios a la vez: tras el primer cambio no queda ningun secreto publico. Por eso NO se cambia el seed: mientras el admin no use "Mi cuenta", su clave `Admin123*` ya es publica de todas formas.
- README: "Cambiar la contraseña y la respuesta en Configuración > Mi cuenta tras el primer ingreso".
- Permisos (seccion 5): fila nueva "Cambiar mi contraseña / pregunta / respuesta: si / si / si".

### 13.8 Limite de 72 bytes (H-29, BAJO, hallazgo del Engineer)
- BCrypt usa solo los primeros 72 BYTES. `Validador.password` y la respuesta de recuperacion cuentan caracteres: 41 `ñ` son 82 bytes y lo que pase de 72 se ignora en silencio. Fix: medir `s.getBytes(StandardCharsets.UTF_8).length <= 72` (contraseña nueva en registro, recuperacion y "Mi cuenta"; respuesta en registro y "Mi cuenta"). Textos: `"La contraseña debe tener entre 8 y 72 caracteres (máx. 72 bytes: tildes y ñ cuentan doble), con letras y números"` y `"La respuesta de recuperación es obligatoria (máx. 72 bytes)"`.

### 13.9 Hash con cost absurdo en BD (mejora del Tester)
- jbcrypt acepta cost 4..30: un hash cost 30 escrito a mano en BD colgaria el login horas en el hilo FX. `coincide()` rechaza antes de calcular si el hash no cumple `private static final Pattern BCRYPT = Pattern.compile("\\$2[ab]\\$(0[4-9]|1[0-4])\\$[./A-Za-z0-9]{53}")` -> `false`. La app solo genera cost 12 y `HASH_DUMMY` cumple el patron. Los hashes raros de H-18 siguen dando `false`.

### 13.10 Revision de capas iteracion 4 (Engineer)
- Repositorios: 100% `PreparedStatement`, sin concatenacion de entrada (grep OK). Validador sin BD ni Sesion. Servicios con `Permisos.exigir` en la primera linea donde aplica. `Db` es el unico punto de conexion.
- Controladores: solo UI -> `Validador` -> servicio -> pintar. Excepciones aceptadas (sin cambio): `Sesion` leida para ocultar/deshabilitar en Principal/Registro/Configuracion (cosmetico); `"Seleccione ..."` por estado de UI; total de referencia en Venta; reagrupado del dashboard; recarga de Inventario por texto del mensaje (13.5). `WindowManager.ajustarAlto` es solo vista.
- Ninguna violacion nueva de capas. Hallazgos nuevos del Engineer: H-28 (ALTO), H-29 (BAJO).

### 13.11 Spec (`contexto.txt`) contra el codigo: revision final
- Cumplido: login, registro y recuperacion con roles ADMINISTRADOR, DUEÑO y CAJERO; usuario con todos los campos y hashes; limite de 4 ventanas en `WindowManager` (unico creador de `Stage`; con una ventana por fxml el maximo real logueado es principal + registro + cajero + categoria = 4, el limite se respeta y el mensaje existe si se alcanza); pestañas [Venta, Producto, Distribuidor, Inventario, Configuración, Cerrar sesión]; producto con combos y boton de categoria; Inventario con registrar, actualizar y eliminar; cajero sin nulos; distribuidora y distribuidor en un formulario, minimo el nombre; venta con combos de cajero y producto, varias lineas y resta de stock atomica; dashboard debajo de las pestañas con 2 secciones (amarillo y rojo; rojo con 1 unidad; umbral por categoria); configuracion con 3 tamaños de letra y umbral por categoria; copo de nieve con hechos `venta` y `producto_vendido`.
- Permisos por rol: tabla de la seccion 5 + "Mi cuenta" (13.7). DUEÑO hace todo menos crear usuarios con rol; CAJERO solo vende, ve el dashboard, cambia su letra y su cuenta.
- Desviaciones documentadas, sin cambio: venta con N lineas en vez de `venta.producto_vendido_id`; el dashboard usa `fecha_expiracion` (la spec dice "fecha de actualizacion"); el combo de producto lista distribuidoras (la FK de la spec es `producto.distribuidora_id`); `cajero` no se enlaza con `usuario`.
- Faltante real encontrado: ninguno funcional de la spec. El unico hueco era de seguridad (H-28).

### 13.12 Ceilings aceptados al cierre y acciones del usuario
- Aceptados (con `// ponytail:` o documentados): JDBC y BCrypt en el hilo FX (H-08; la UI se congela como mucho lo que dure el timeout); contadores de bloqueo en memoria, que se pierden al reiniciar, y bloqueo de 5 min de "admin" por terceros (R5, DoS local); "El username ya existe" en el auto-registro (oraculo inherente); campos de producto distintos del stock con last-write-wins; `igual()` sensible a tildes (13.6).
- Acciones del usuario (no las puede hacer ningun agente):
  1. Mover la config a `%USERPROFILE%\.inventario\db.properties` (recomendado; hoy esta en el classpath, valido solo para `mvn javafx:run`).
  2. Para verificar en InnoDB (R-H19, H-19, H-23, carga de `schema.sql`): un administrador de MySQL ejecuta `GRANT CREATE, DROP, ALTER, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON inventario_tienda_test.* TO '<usuario_app>'@'localhost';` y luego corre `MySqlRealTest` (README, seccion Pruebas).
  3. Tras el primer ingreso con `admin`: Configuración > Mi cuenta.
