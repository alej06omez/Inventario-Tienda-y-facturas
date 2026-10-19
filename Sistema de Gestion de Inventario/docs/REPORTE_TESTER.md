# Reporte del Tester

## Iteracion 1


mvn -q test OK: 37 tests, 34 pasan, 3 @Disabled = hallazgos abiertos (H-01, H-05, H-09) en IntegracionH2Test.
Ver fallar: mvn test -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition -Dtest=IntegracionH2Test
H2 2.3.232 scope test; src/test/resources/db.properties -> H2 MODE=MySQL. Solo incompatibilidad schema.sql: CREATE DATABASE/USE.

Funciona: SQL injection (8 cargas, todas literales), login no enumerable, BCrypt $2a$12$, R5 bloqueo, R6 sin escalada, permisos en servicio, nulos por formulario, venta multi-linea + rollback, concurrencia 12 hilos/5 unidades OK, dashboard bordes, umbral, fuente persiste, ON DELETE SET NULL, sin fugas JDBC.

Conteo: CRITICO 0, ALTO 2, MEDIO 4, BAJO 10

H-01 ALTO service/AuthService.java:32 (:62,:71) bloqueo evadible: clave username.toLowerCase() vs utf8mb4_unicode_ci (ignora tildes/mayusculas). "víctima" entra tras bloquear "victima". Fix: contar por id de usuario devuelto por BD (fallback "u:"+normalizado), toLowerCase(Locale.ROOT), igual en recuperar. Test bloqueoNoEvadiblePorTildes.
H-02 ALTO src/main/resources/db.properties:2-4 root/root y ?user=rootmysql en URL; va en el jar. Rompe R9. NOTA ORQUESTADOR: estos valores los puso el usuario; no sobrescribirlos. Fix: leer config fuera del classpath (~/.inventario/db.properties o -Ddb.config=), fallback a classpath; quitar db.properties de resources empaquetados; documentar usuario MySQL dedicado; avisar sobre ?user= en URL.
H-03 MEDIO AuthService.java:55-58 preguntaDe enumera usuarios (pregunta fija para inexistentes) y sin limite. Fix: pregunta plausible por Math.floorMod(hash(username normalizado), N) de lista fija; aplicar contador R5.
H-04 MEDIO controladores solo atrapan AppException; App.java sin handler global. Db static init -> ExceptionInInitializerError si falta db.properties; BCrypt.checkpw IllegalArgumentException con hash malo; NPE. Fix: uncaught handler en hilo FX con alerta "Error inesperado"; Db carga perezosa lanzando AppException; atrapar IllegalArgumentException en checkpw.
H-05 MEDIO repository/DistribuidoraRepository.java:22 INSERT IGNORE descarta datos del distribuidor con cedula existente en silencio (y oculta otros errores en MySQL). Fix: INSERT normal; en duplicado SELECT y si difiere AppException. Test cedulaExistenteNoDescartaDatosEnSilencio.
H-06 MEDIO RegistroController.java:25, LoginController.java:28-33, principal.fxml: ADMIN no puede crear DUENO/ADMIN desde UI (registro solo desde login sin sesion). Fix: boton "Registrar usuario" en principal/Configuracion visible para ADMINISTRADOR.
H-07 BAJO LoginController.java:19-21 login exitoso sin principal si ya hay 4 ventanas -> logout si abrir devuelve null o chequear cupo antes.
H-08 BAJO Db.java:25, AuthService.java:77 JDBC/BCrypt en hilo FX sin timeouts -> connectTimeout=5000&socketTimeout=15000; Task para login/registro/venta.
H-09 BAJO validator/Validador.java:13 telefono solo espacios/guiones pasa -> ^(?=(?:\D*\d){7})[0-9+ -]{7,20}$. Test telefonoSoloEspaciosRechazado.
H-10 BAJO repository/VentaRepository.java:34 "Stock insuficiente para el producto 57" con producto borrado -> si UPDATE==0, SELECT nombre,cantidad: "Producto no encontrado" o "Stock insuficiente para <nombre> (disponible N)".
H-11 BAJO registro.fxml:13, recuperar.fxml:15 respuesta en TextField -> PasswordField. (enviado a UX_UI iter1)
H-12 BAJO DashboardController.java:39 solo color, no agrupa por categoria -> etiqueta texto + agrupar. (enviado a UX_UI iter1)
H-13 BAJO service/CategoriaService.java:15 default umbral 0->10 inalcanzable (controlador valida antes). Borrar o hacer vacio->default.
H-14 BAJO view/inventario.fxml:26-27 Inventario no permite "registrar" (spec linea 31). Boton "Nuevo".
H-15 BAJO VentaRepository.java:59, DistribuidoraRepository.java:45 rollback en catch tapa excepcion -> addSuppressed.
H-16 BAJO WindowManager.java:58-69 fuente via setStyle pisa estilos y no llega a Alert. (enviado a UX_UI iter1)

Mejoras: R5 DoS bloqueando admin (documentar/progresivo); forzar cambio de clave admin seed en primer ingreso; ventanas duplicadas -> traer al frente (UX_UI); TestFX para limite 4 ventanas; DatePicker sin Enter (UX_UI); confirmar antes de venta (UX_UI); mantener IntegracionH2Test y quitar @Disabled al corregir.

## Iteracion 2

`mvn -q test` OK: 60 tests, 54 pasan, 6 @Disabled = hallazgos nuevos abiertos (H-17 x2, H-18, H-19, H-21 en `IntegracionH2Test`; H-20 en `DbConfigTest`).
Ver fallar: `mvn test -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition` (6 fallos esperados).
Tests nuevos: 17 en `IntegracionH2Test` (seccion "Tester iteracion 2") + `DbConfigTest` (6, reflexion sobre `Db.props()`, `user.home` en `@TempDir`, sin conexiones).
Jar (`mvn -q package -DskipTests`) NO contiene `db.properties`. No se leyo `src/main/resources/db.properties` ni se conecto a MySQL.

Conteo nuevos: CRITICO 0, ALTO 0, MEDIO 1, BAJO 5

### Hallazgos nuevos

H-17 MEDIO enumeracion de usuarios sigue posible (H-03 parcial). `service/AuthService.java:63-64`, `view/registro.fxml:12`, `schema.sql:112`.
- (a) Pregunta propia es texto libre, casi nunca en `PREGUNTAS_FALSAS`: `preguntaDe("admin")` = "Nombre de la tienda?" -> admin existe. Test `h17PreguntaPropiaSaleDeLaMismaLista`.
- (b) Variantes con tilde: existente -> misma pregunta; inexistente -> `hashCode` de variantes da preguntas distintas (4 en 5). Test `h17PreguntaNoDistingueExistenciaPorVariantes`.
- (c) Registro sin sesion responde "El username ya existe" (`UsuarioRepository.java:41`): oraculo inherente al auto-registro.
- Sugerencia: (a) ComboBox en registro con la MISMA lista (`AuthService.PREGUNTAS`) y migrar seed; (b) normalizar antes del hash: `Normalizer.normalize(s, NFKD).replaceAll("\\p{M}", "")` + `toLowerCase(Locale.ROOT)`. Si (c) es aceptable, documentar y bajar a BAJO.

H-18 BAJO hash corto en BD revienta login. `service/AuthService.java:94-100`. `coincide` solo atrapa `IllegalArgumentException`; jbcrypt 0.4 lanza `StringIndexOutOfBoundsException` con `""`, `"$2a"`, `"$2a$12$corto"`. Test `h18HashTruncadoNoRevienta`. Sugerencia: atrapar `IndexOutOfBoundsException` o `hash == null || hash.length() != 60 -> false`.

H-19 BAJO carrera en alta con cedula nueva. `repository/DistribuidoraRepository.java:21-41, 62-63`. Dos altas simultaneas misma cedula nueva: la 2a choca en INSERT por PK y se traduce a "El distribuidor indicado no existe" (falso). Test `h19CedulaNuevaConcurrente`. Sugerencia: en duplicado repetir una vez SELECT+comparacion, o mensaje "Otro usuario registró la cédula X; intente de nuevo".

H-20 BAJO `-Ddb.config` con ruta invalida no da `AppException`. `Db.java:24-25`. `Path.of(ruta)` lanza `InvalidPathException`. Test `DbConfigTest.dbConfigRutaInvalidaDaAppException`. Sugerencia: `catch (InvalidPathException e) -> AppException("Ruta de configuración inválida: " + ruta, e)`.

H-21 BAJO mensaje de H-05 propone algo que no funciona. `repository/DistribuidoraRepository.java:29-30`, `validator/Validador.java:70`, `service/DistribuidoraService.java:19`. Dejar solo cedula -> "El nombre del distribuidor es obligatorio"; todo vacio -> distribuidora sin vincular. Test `h21VincularDistribuidorExistenteSoloConCedula`. Sugerencia: permitir distribuidor con solo cedula (nombre obligatorio solo si viene otro campo); si cedula no existe y no hay nombre -> `AppException("No existe un distribuidor con cédula X; ingrese su nombre")`. Minimo: cambiar el texto.

H-22 BAJO ortografia: `controller/InventarioController.java:122` y `controller/VentaController.java:87` sin "¿" inicial.

### Estado H-01..H-16

| Id | Estado | Evidencia |
|----|--------|-----------|
| H-01 | CERRADO | Clave `id:N`; variantes mayusculas/tildes/espacios Unicode bloqueadas. Residual aceptado: DoS 5 min bloqueando "admin". |
| H-02 | PARCIAL | Codigo cerrado (config externa, jar sin db.properties, .gitignore). Pendiente usuario: mover credenciales a `~/.inventario/db.properties` con usuario dedicado. |
| H-03 | PARCIAL | Pregunta estable, bloqueo `rec:`, tiempos similares. Sigue enumerable -> H-17. |
| H-04 | CERRADO (residual H-18, H-20) | Db perezoso, handler global, hash no-bcrypt manejado. |
| H-05 | CERRADO (residual H-19, H-21) | Mensaje, rollback, reuso. |
| H-06 | CERRADO | Seccion Usuarios solo ADMIN; R6 en servicio. |
| H-07 | CERRADO | `cerrarTodas()` antes de principal; fallo -> logout. |
| H-08 | PARCIAL (aceptado) | Timeouts en example/README; JDBC/BCrypt en hilo FX. |
| H-09 | CERRADO | Telefono minimo 7 digitos. |
| H-10 | CERRADO | Mensajes de stock/producto + rollback. |
| H-11 | CERRADO | PasswordField. |
| H-12 | CERRADO | Texto de nivel + agrupado por categoria. |
| H-13 | CERRADO | Linea muerta borrada. |
| H-14 | CERRADO | Nuevo/Registrar en Inventario. |
| H-15 | CERRADO | `addSuppressed`. |
| H-16 | CERRADO | Clase `letra-*` en raiz y DialogPane. |

### Regresion
Todo OK: SQL injection (8 cargas, todas las entradas, 0 SQL concatenado), permisos, nulos, venta multi-linea/rollback/12 hilos, dashboard/umbral, orden de carga de `Db`, WindowManager (limite 4, reuso), sin fugas JDBC.

### Mejoras (no bloqueantes)
- `AuthService.fallos/bloqueadoHasta` nunca se purgan ni decaen; reiniciar contador tras `BLOQUEO`.
- `Db`: validar claves al cargar ("Falta db.url en RUTA").
- CAJERO ve "Guardar umbral" en Configuracion: ocultarlo.
- Inventario: verificar si tras "Actualizar" se pierde la seleccion; reseleccionar por id.
- `HASH_DUMMY` igual al hash del admin seed: usar dummy propio.

## Iteracion 3

### Resultados
- `mvn -q clean test` OK: **87 tests, 0 fallos, 17 omitidos** = 15 de `MySqlRealTest` (opcional: sin `-Dmysql.test=true` se omite) + 2 `@Disabled` = hallazgos nuevos abiertos (H-24, H-25 en `IntegracionH2Test`). Por clase: Dashboard 2, DbConfig 9, IntegracionH2 51 (2 omitidos), MySqlReal 15 (omitidos), Validador 10.
- Ver fallar H-24/H-25: `mvn test -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition "-Dtest=IntegracionH2Test#h24*+h25*"` (2 fallos esperados, verificado).
- Tests nuevos: `IntegracionH2Test` seccion "Tester iteracion 3" (8: H-17 checklist, H-24, H-18 hashes/dummy, contadores, H-25, H-19 datos distintos, H-21 cedula+apellidos, H-23 producto borrado/reabastecer/mismos valores); `DbConfigTest` +2 (claves obligatorias + password opcional + no cacheo; ruta con espacio final); `MySqlRealTest` (15, opcional, sin credenciales).
- **Corrida MySQL real: BLOQUEADA por privilegios (0 de 15 ejecutados).** La cuenta de `src/main/resources/db.properties` es un usuario dedicado con solo `SELECT, INSERT, UPDATE, DELETE ON inventario_tienda.*` (+ `USAGE`): `DROP/CREATE DATABASE inventario_tienda_test` -> error 1044. Sin crear usuarios ni cambiar permisos (prohibido) no se puede crear la BD de prueba. `inventario_tienda_test` **nunca se creo**; `inventario_tienda` no se toco ni se leyo.
- Lo que SI se verifico en MySQL real (8.0.44, conexion sin BD, solo `SELECT` de expresiones, sin tablas): collation `utf8mb4_unicode_ci` de 33 variantes de username contra `canon()` (-> H-24); round-trip utf8mb4 por JDBC (`Café Ñandú ¿Qué tal? — 漢字 😀🧾`: igual, 28 caracteres, 45 bytes); `CURDATE()` = `LocalDate.now()` de la JVM (JVM `America/Bogota`, servidor `time_zone=SYSTEM`, hora de Sudamerica Pacifico); `sql_mode` estricto (`STRICT_TRANS_TABLES`, ...); `REPEATABLE-READ`; `innodb_lock_wait_timeout=50`; `collation_connection=utf8mb4_0900_ai_ci` (la columna `unicode_ci` gana por coercibilidad).
- Para completar la corrida (decision del usuario): (a) un admin de MySQL ejecuta `GRANT CREATE, DROP, ALTER, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON inventario_tienda_test.* TO '<usuario_app>'@'localhost';`, o (b) entrega una cuenta administradora en un archivo aparte. Luego: `mvn test -Dtest=MySqlRealTest -Dmysql.test=true -Dmysql.config=RUTA\test-db.properties -Dmysql.schema=RUTA\schema_test.sql`. La clase exige que `db.url` apunte a `/inventario_tienda_test?` y que la copia del schema no nombre `inventario_tienda`; carga el schema 3 veces (idempotencia + migracion del seed) y hace `DROP DATABASE` + verificacion en `@AfterAll`.
- Jar (`mvn -q package -DskipTests`): solo `db.properties.example`, sin `db.properties`. Grep: `setStyle` solo en `WindowManager.java:81` (U2 pendiente); 0 `Statement` sin `Prepared` en `src/main`; 0 concatenacion de entrada en SQL de `repository/`.

Conteo nuevos: CRITICO 0, ALTO 0, MEDIO 1, BAJO 3

### Hallazgos nuevos

H-24 BAJO (H-17 residual, verificado en MySQL real) `service/AuthService.java:101-102`: `canon()` no cubre equivalencias que `utf8mb4_unicode_ci` si aplica.
- Controles `\p{Cc}` (U+0001, U+0007, U+007F, en medio o al final): MySQL los ignora, `canon()` los conserva.
- Espacios no separables finales (U+00A0, U+2007, U+202F): MySQL los iguala por PAD SPACE; `strip()` va ANTES de NFKD y `Character.isWhitespace` excluye los no separables.
- `ß`=`ss`: ya documentado (ceiling b).
- Reproduccion: `preguntaDe("usuario_real ")` = su pregunta real (la BD lo encuentra); `preguntaDe("inexistente ")` = otra pregunta con prob. 5/6 -> distingue existencia. Igual con el bloqueo: tras 5 fallos sobre un inexistente, `"inexistente "` no esta bloqueado; sobre uno existente si (clave `id:N`). Test `h24CanonCubreLoQueMySqlIgnora` (@Disabled). `MySqlRealTest.collationVsCanonH01H17` y `bloqueoComoOraculoConNbsp` lo reproducen con tablas.
- Inocuo: U+00AD en medio (MySQL lo distingue, `canon()` lo quita: solo hace el inexistente mas estable). NBSP en medio, `×`, `‗`, `ɑ`, `æ`: ni MySQL ni `canon()` igualan.
- Sugerencia (validada con 13 variantes): `Normalizer.normalize(username, NFKD).replaceAll("[\\p{M}\\p{Cf}\\p{Cc}]", "").strip().toLowerCase(Locale.ROOT).replace("ß", "ss")` (strip DESPUES de NFKD: NFKD convierte U+00A0/U+2007/U+202F en U+0020). H-01 no cambia: el bloqueo de existentes sigue por id.

H-25 BAJO `service/AuthService.java:131`: la purga solo se dispara con `fallos.size() > MAX_CLAVES`; al bloquear, la clave sale de `fallos` y entra en `bloqueadoHasta`, que nunca dispara la purga. Claves bloqueadas que no se vuelven a consultar se acumulan sin cota (5 BCrypt por clave, ~2.900 claves/hora en hilo FX). Test `h25BloqueosVencidosTambienSePurgan` (@Disabled): 10.001 bloqueos vencidos + un fallo -> siguen todos. Sugerencia: `if (fallos.size() + bloqueadoHasta.size() > MAX_CLAVES) purgar(ahora);`.

H-26 MEDIO (configuracion del usuario, no codigo) `src/main/resources/db.properties:2-4`: la app no puede conectarse con la config actual.
- `db.user` esta escrito con sintaxis SQL de cuenta (`'nombre'@'host'`) y `db.password` entre comillas: `Properties` los toma literales -> MySQL "Access denied" -> el login solo muestra "Error de base de datos". Verificado: con el nombre sin comillas ni @host y la clave sin comillas (solo en la copia del scratchpad, ya borrada) la autenticacion funciona.
- `db.url` tiene `??useSSL=...`: el primer parametro queda con nombre `?useSSL` y Connector/J lo ignora (probable: `useSSL=false` no se aplica).
- Lo bueno: ya es un usuario dedicado con solo DML sobre `inventario_tienda` y la URL ya no trae `?user=` (H-02 avanza).
- Sugerencia usuario: `db.user=nombre` y `db.password=clave` sin comillas, un solo `?` en la URL, mover el archivo a `%USERPROFILE%\.inventario\db.properties`. Sugerencia codigo (BAJO, `Db.java:48` + repos): si `SQLException.getSQLState()` empieza con `28` -> `AppException("No se pudo conectar a MySQL: usuario o contraseña incorrectos (revise db.user/db.password, sin comillas)")`; en `props()` rechazar `db.user` con `'` o `@` ("db.user debe ser solo el nombre del usuario, sin comillas ni @host"). README: una linea con ese ejemplo.

H-27 BAJO (UX) `controller/InventarioController.java:120` + `:61`: ante CUALQUIER error del servicio en "Actualizar" (no solo H-23: tambien FK "La distribuidora o la categoría no existe", "Producto no encontrado", permisos) `refrescar()` re-selecciona la fila y el listener `cargar()` sobrescribe lo tecleado. Si `refrescar()` falla, muestra su propio error y luego el catch externo otro (dos dialogos). Sugerencia: recargar solo cuando el error es el de stock cambiado (p. ej. comparar el prefijo del mensaje) y que `refrescar()` no muestre error cuando lo llama otro handler.

### Riesgo MySQL sin verificar (bloqueado por privilegios)
- R-H19 `repository/DistribuidoraRepository.java:33,63`: en InnoDB, cada INSERT que choca por clave duplicada deja un lock S sobre la fila existente; con 3+ cajas a la vez, todas piden despues `FOR UPDATE` (X) sobre esa fila -> patron clasico de deadlock S->X (error 1213 -> "Error de base de datos" + rollback). H2 no lo reproduce. Si `MySqlRealTest.h19CedulaNuevaConcurrente*` lo confirma: usar `FOR SHARE` (tambien lee el ultimo commit, y S es compatible con S).
- Pendiente con tablas (todo escrito en `MySqlRealTest`): carga real e idempotencia de `schema.sql` + migracion del seed, login admin seed, CHECK 3819, ON DELETE SET NULL / ON UPDATE CASCADE, venta 20 hilos x 3 rondas, H-23 concurrente, UPDATE con mismos valores (filas encontradas vs cambiadas), `fecha_registro` vs hora JVM, `LÁCTEOS` vs seed `Lacteos` (UNIQUE ci), inyeccion con cargas MySQL (`#`, `/*`, `\'`, `SLEEP`, apostrofos Unicode), PROCESSLIST sin fugas.

### Estado H-17..H-23

| Id | Estado | Evidencia |
|----|--------|-----------|
| H-17 | CERRADO (residual H-24) | Lista unica de 6, ComboBox, servicio rechaza pregunta libre/inyeccion y acepta valida con espacios (guardada sin ellos); seed y migracion en la lista; tilde/mayus/ancho completo/U+200B/U+00AD/U+FEFF -> una pregunta. MySQL real: controles y no separables finales siguen distinguibles (H-24). |
| H-18 | CERRADO | `""`, `"$2a"`, `$2a$12$corto`, 60 basura, version `$2x`, cost 99 -> `false` sin excepcion; ratio de tiempos sigue 0,5-2; `HASH_DUMMY` 60 chars, no esta en `schema.sql`, no coincide con `Admin123*`/`tienda`. |
| H-19 | CERRADO en H2 / PENDIENTE MySQL | 6 hilos mismos datos -> todos ok, 1 fila; datos distintos -> 1 ok + 5 mensajes H-05, 1 distribuidora (rollback). InnoDB: ver R-H19. |
| H-20 | CERRADO | NUL y espacio final -> `AppException`; sin `db.url` o `db.user` en blanco -> mensaje; sin `db.password` permitido; config invalida no se cachea; directorio -> mensaje legible. |
| H-21 | CERRADO | Solo cedula existente -> vincula; inexistente sin nombre -> "ingrese su nombre"; cedula + apellidos (o email) coincidentes sin nombre -> vincula; distinto -> H-05 nuevo con apellidos; nombre >100 rechazado. |
| H-22 | CERRADO | "¿" (verificado por el Engineer). |
| H-23 | CERRADO en H2 | Venta intermedia -> mensaje y stock intacto; producto borrado -> "Producto no encontrado"; FK invalida -> su mensaje; CAJERO sin permiso; reabastecer con la cantidad leida OK; mismos valores exactos OK. UX: H-27. |

Checklist del Engineer (9 puntos): 1 OK (+H-24), 2 OK, 3 OK (+H-25), 4 OK, 5 OK en H2 (MySQL pendiente, R-H19), 6 OK, 7 OK, 8 OK (inyeccion, permisos, 12 hilos, dashboard, fugas, jar), 9 OK (solo queda el `setStyle` de U2).

### Diferencias H2 vs MySQL
- Collation: H2 `SET COLLATION ENGLISH STRENGTH PRIMARY` aproxima `utf8mb4_unicode_ci` en tildes/mayusculas, pero MySQL ademas ignora controles `\p{Cc}` y aplica PAD SPACE a no separables finales (H-24); `ß`=`ss`.
- Aislamiento: H2 lee el ultimo commit en lecturas normales; InnoDB `REPEATABLE-READ` lee el snapshot. Por eso H-19 necesita lectura bloqueante, y por eso el riesgo S->X no aparece en H2.
- `collation_connection` = `utf8mb4_0900_ai_ci` en MySQL: inocuo (la columna define la comparacion); solo literal contra literal usaria 0900.
- `schema.sql`: H2 omite `CREATE DATABASE`/`USE`; en MySQL solo se verifico el texto de la copia (carga real bloqueada).
- Fechas: el dashboard usa `LocalDate.now()` de la JVM como parametro (no `CURDATE()`); en esta maquina coinciden. Con servidor en otra zona, `fecha_registro`/`fecha_actualizacion` (`CURRENT_TIMESTAMP` del servidor) podrian diferir; `fecha_expiracion` (DATE + `LocalDate`) no se corre.

### Mejoras (no bloqueantes)
- README: como correr `MySqlRealTest` (opcional) con una cuenta con privilegios solo sobre `inventario_tienda_test`.
- `DistribuidoraRepository.java:43`: con cedula existente, guardar en `distribuidora.distribuidor_id` la cedula tal como esta en BD (hoy se guarda la tecleada, p. ej. `m21-1` frente a la PK `M21-1`; MySQL ci lo acepta pero queda inconsistente a la vista).
- `DistribuidoraRepository.java:78` `igual()` usa `equalsIgnoreCase` (sensible a tildes): `Jose` vs `José` da H-05. Aceptable; documentar o comparar con `canon`.
- Un hash con cost 30 en BD colgaria el login horas (jbcrypt acepta 4..30); solo posible con escritura directa en BD -> documentar.

## Iteracion 4 (FINAL)

Veredicto: **LISTO PARA ENTREGA**. `mvn -B clean package`: 102 tests, 0 fallos, 15 omitidos (solo `MySqlRealTest`), 0 `@Disabled`; jar solo con `db.properties.example`. Checklist de cierre 1-10 OK (11 = PENDIENTE USUARIO). Hallazgo nuevo: H-30 BAJO (credenciales en URL con `user:pass@host` o `(user=..)` no detectadas por `Db.props()`; sugerido ACEPTADO). Checklist con evidencia, smoke JavaFX H2 de Mi cuenta y tabla final H-01..H-30 + R-H19: `docs/BITACORA.md`, seccion "Iteracion 4 - Tester".
