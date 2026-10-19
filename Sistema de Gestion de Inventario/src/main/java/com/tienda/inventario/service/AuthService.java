package com.tienda.inventario.service;

import com.tienda.inventario.AppException;
import com.tienda.inventario.auth.Sesion;
import com.tienda.inventario.model.Rol;
import com.tienda.inventario.model.Usuario;
import com.tienda.inventario.repository.UsuarioRepository;
import com.tienda.inventario.validator.Validador;
import org.mindrot.jbcrypt.BCrypt;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AuthService {
    private AuthService() {}

    /** Hash cost 12 contra el que se compara cuando el usuario no existe (R3: tiempo similar); no es el hash de ningun usuario. */
    private static final String HASH_DUMMY = "$2a$12$nSGEHqbx4IeJTuAsjcOoyeZixRl0oJvrgYSmlkEcCt1CbiNAzOF6C";
    // ponytail: usuarios previos a iter 3 con pregunta libre siguen distinguibles; aceptado (ARQUITECTURA 12.1).
    /** Unica lista: ComboBox de registro y pregunta falsa de usuarios inexistentes. */
    public static final List<String> PREGUNTAS = List.of(
            "¿Cuál es el nombre de su primera mascota?", "¿En qué ciudad nació su madre?", "¿Cuál fue su primer colegio?",
            "¿Cuál es su comida favorita?", "¿Cómo se llamaba su mejor amigo de infancia?", "¿Cuál es el nombre de su tienda?");
    private static final int MAX_FALLOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(5);
    private static final Pattern BCRYPT = Pattern.compile("\\$2[ab]\\$(0[4-9]|1[0-4])\\$[./A-Za-z0-9]{53}");

    // ponytail: en memoria, se pierde al reiniciar; mover a columna en BD si hace falta. Sin sincronizar: solo hilo FX.
    // ponytail: sin limite duro; cada intento cuesta un BCrypt (~1.200 claves por ventana). LRU/BD si se expone por red.
    private record Fallos(int n, Instant desde) {}
    private static final Map<String, Fallos> fallos = new HashMap<>();
    private static final int MAX_CLAVES = 10_000;
    private static final Map<String, Instant> bloqueadoHasta = new HashMap<>();

    public static Usuario login(String username, String password) {
        Validador.login(username, password);
        Optional<Usuario> u = UsuarioRepository.porUsername(username.strip());
        String key = "login:" + clave(u, username);
        verificarBloqueo(key);
        boolean ok = coincide(password, u.map(Usuario::password).orElse(HASH_DUMMY)) && u.isPresent();
        if (!ok) {
            fallo(key);
            throw new AppException("Usuario o contraseña incorrectos");
        }
        limpiar(key);
        Sesion.iniciar(u.get());
        return Sesion.actual();
    }

    /** R6: el rol pedido solo se respeta si quien registra es ADMINISTRADOR; si no, CAJERO. */
    public static int registrar(Usuario u) {
        Validador.usuario(u);
        if (!PREGUNTAS.contains(u.preguntaRecover().strip())) throw new AppException("Seleccione una pregunta de la lista");
        Usuario s = Sesion.actual();
        Rol rol = s != null && s.rol() == Rol.ADMINISTRADOR && u.rol() != null ? u.rol() : Rol.CAJERO;
        return UsuarioRepository.insertar(new Usuario(null, u.nombre().strip(), u.username(), hash(u.password()), u.telefono().strip(),
                u.preguntaRecover().strip(), hash(normalizar(u.respuestaRecover())), rol));
    }

    /** R4: si el usuario no existe, pregunta plausible y estable por username. Respeta el bloqueo de recuperacion. */
    public static String preguntaDe(String username) {
        if (username == null || username.isBlank()) return PREGUNTAS.getFirst();
        Optional<Usuario> u = UsuarioRepository.porUsername(username.strip());
        verificarBloqueo("rec:" + clave(u, username));
        return u.map(Usuario::preguntaRecover).orElseGet(() ->
                PREGUNTAS.get(Math.floorMod(canon(username).hashCode(), PREGUNTAS.size())));
    }

    public static void recuperar(String username, String respuesta, String nuevaPassword) {
        Validador.recuperacion(username, respuesta, nuevaPassword);
        Optional<Usuario> u = UsuarioRepository.porUsername(username.strip());
        String id = clave(u, username), key = "rec:" + id;
        verificarBloqueo(key);
        boolean ok = coincide(normalizar(respuesta), u.map(Usuario::respuestaRecover).orElse(HASH_DUMMY)) && u.isPresent();
        if (!ok) {
            fallo(key);
            throw new AppException("Datos de recuperación incorrectos");
        }
        limpiar(key);
        limpiar("login:" + id);
        UsuarioRepository.actualizarPassword(u.get().id(), hash(nuevaPassword));
    }

    /** H-28 "Mi cuenta": cambia clave, pregunta y respuesta del usuario en sesion; comparte el bloqueo del login. */
    public static void cambiarCredenciales(String actual, String nueva, String pregunta, String respuesta) {
        Usuario s = Permisos.exigir();
        Validador.cambioCredenciales(actual, nueva, pregunta, respuesta);
        if (!PREGUNTAS.contains(pregunta.strip())) throw new AppException("Seleccione una pregunta de la lista");
        Usuario u = UsuarioRepository.porUsername(s.username()).orElseThrow(() -> new AppException("Debe iniciar sesión"));
        String id = "id:" + u.id(), key = "login:" + id;
        verificarBloqueo(key);
        if (!coincide(actual, u.password())) {
            fallo(key);
            throw new AppException("La contraseña actual no es correcta");
        }
        UsuarioRepository.actualizarCredenciales(u.id(), hash(nueva), pregunta.strip(), hash(normalizar(respuesta)));
        limpiar(key);
        limpiar("rec:" + id);
    }

    public static void logout() { Sesion.cerrar(); }

    private static String hash(String s) { return BCrypt.hashpw(s, BCrypt.gensalt(12)); }

    private static String normalizar(String respuesta) { return respuesta.strip().toLowerCase(Locale.ROOT); }

    /** H-01: la identidad la decide la BD (su collation), no el texto tecleado. */
    private static String clave(Optional<Usuario> u, String username) {
        return u.map(x -> "id:" + x.id()).orElseGet(() -> "u:" + canon(username));
    }

    /** Aproxima utf8mb4_unicode_ci: sin tildes, ancho completo, formato ni controles; strip tras NFKD (NBSP finales = PAD SPACE); ß=ss. */
    private static String canon(String username) {
        return Normalizer.normalize(username, Normalizer.Form.NFKD).replaceAll("[\\p{M}\\p{Cf}\\p{Cc}]", "").strip()
                .toLowerCase(Locale.ROOT).replace("ß", "ss");
    }

    /**
     * Hash corrupto en BD = no coincide. Primero el patron BCRYPT ($2a/$2b, cost 04..14): cost > 14 escrito a mano en BD
     * colgaria el hilo FX; la app solo genera cost 12. Luego jbcrypt 0.4, que lanza IllegalArgumentException,
     * o StringIndexOutOfBoundsException con hash corto.
     */
    private static boolean coincide(String plano, String hash) {
        if (hash == null || !BCRYPT.matcher(hash).matches()) return false;
        try {
            return BCrypt.checkpw(plano, hash);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static void verificarBloqueo(String key) {
        Instant hasta = bloqueadoHasta.get(key);
        if (hasta == null) return;
        if (Instant.now().isBefore(hasta)) throw new AppException("Demasiados intentos fallidos. Intente de nuevo en unos minutos");
        bloqueadoHasta.remove(key);
    }

    /** Ventana de BLOQUEO desde el primer fallo; vencida, la cuenta reinicia. */
    private static void fallo(String key) {
        Instant ahora = Instant.now();
        Fallos f = fallos.get(key);
        if (f == null || !ahora.isBefore(f.desde().plus(BLOQUEO))) f = new Fallos(0, ahora);
        f = new Fallos(f.n() + 1, f.desde());
        if (f.n() >= MAX_FALLOS) {
            fallos.remove(key);
            bloqueadoHasta.put(key, ahora.plus(BLOQUEO));
        } else fallos.put(key, f);
        if (fallos.size() + bloqueadoHasta.size() > MAX_CLAVES) purgar(ahora);
    }

    private static void purgar(Instant ahora) {
        fallos.values().removeIf(x -> !ahora.isBefore(x.desde().plus(BLOQUEO)));
        bloqueadoHasta.values().removeIf(h -> !ahora.isBefore(h));
    }

    private static void limpiar(String key) {
        fallos.remove(key);
        bloqueadoHasta.remove(key);
    }
}
