package com.tienda.inventario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Fuente unica de conexiones JDBC. Config cargada en el primer get(), buscada en este orden:
 * -Ddb.config=RUTA (si no existe: error, sin fallback) > ~/.inventario/db.properties > /db.properties del classpath.
 */
public final class Db {
    private static Properties props;

    private Db() {}

    private static synchronized Properties props() {
        if (props != null) return props;
        String ruta = System.getProperty("db.config");
        Path archivo;
        try {
            archivo = ruta != null ? Path.of(ruta) : Path.of(System.getProperty("user.home"), ".inventario", "db.properties");
        } catch (InvalidPathException e) {
            throw new AppException("Ruta de configuración inválida: " + ruta, e);
        }
        if (ruta != null && !Files.exists(archivo)) throw new AppException("No existe el archivo de configuración " + ruta);
        try (InputStream in = Files.exists(archivo) ? Files.newInputStream(archivo) : Db.class.getResourceAsStream("/db.properties")) {
            if (in == null) throw new AppException("Falta la configuración de la base de datos (ver README.md)");
            Properties p = new Properties();
            p.load(in);
            for (String k : List.of("db.url", "db.user"))
                if (p.getProperty(k, "").isBlank()) throw new AppException("Falta " + k + " en la configuración de la base de datos (ver README.md)");
            if (Pattern.compile("[\\s'\"@]").matcher(p.getProperty("db.user")).find())
                throw new AppException("db.user debe ser solo el nombre del usuario de MySQL, sin comillas, espacios ni @host (ver README.md)");
            String url = p.getProperty("db.url");
            if (url.contains("??")) throw new AppException("db.url tiene \"??\": use un solo ? antes de los parámetros (ver README.md)");
            if (Pattern.compile("(?i)[?&;](user|password)=").matcher(url).find())
                throw new AppException("db.url no debe incluir user= ni password=: van en db.user y db.password (ver README.md)");
            return props = p;
        } catch (IOException e) {
            throw new AppException("No se pudo leer la configuración de la base de datos", e);
        }
    }

    // ponytail: conexion nueva por operacion, sin pool; agregar HikariCP si hay latencia medible.
    public static Connection get() throws SQLException {
        Properties p = props();
        try {
            return DriverManager.getConnection(p.getProperty("db.url"), p.getProperty("db.user"), p.getProperty("db.password"));
        } catch (SQLException e) {
            // H-26: AppException no es SQLException, atraviesa los catch de los repos con un mensaje claro.
            String s = e.getSQLState() == null ? "" : e.getSQLState();
            if (s.startsWith("28")) throw new AppException("No se pudo conectar a MySQL: usuario o contraseña incorrectos (revise db.user y db.password, sin comillas)", e);
            if (s.startsWith("08")) throw new AppException("No se pudo conectar a MySQL: revise que el servidor esté encendido y la dirección de db.url", e);
            throw e;
        }
    }

    /** Id autogenerado tras executeUpdate() de un PreparedStatement creado con RETURN_GENERATED_KEYS. */
    public static int generatedKey(java.sql.PreparedStatement ps) throws SQLException {
        try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
