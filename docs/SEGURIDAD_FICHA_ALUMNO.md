# Acceso a la ficha del alumno

Se integra ramaLucas en ramaAugusto conservando las pruebas de baja administrativa.

## GET /api/alumnos/{id}

- ADMIN puede consultar cualquier ficha.
- ALUMNO puede consultar exclusivamente la ficha vinculada a su usuario autenticado.
- La identidad se resuelve en el servidor mediante AuthService; no se toma del ID solicitado.
- Para un ID ajeno se devuelve 403 antes de consultar la ficha, exista o no.
- Se mantienen la respuesta AlumnoResponseDto y las restricciones de roles existentes.

AlumnoControllerSecurityTest utiliza MockMvc, la configuracion real de Spring Security y su filtro JWT, con servicios simulados. Verifica ficha propia, ficha ajena, ID ajeno inexistente, administrador, solicitud anonima y rol no permitido. Las identidades se inyectan con spring-security-test: estas pruebas no validan la emision ni la firma de tokens JWT.

Ejecutar `./mvnw.cmd test` con Java 17. Las pruebas de persistencia usan H2; el sembrado de datos esta desactivado y la clave JWT de pruebas es exclusiva de ese entorno. No se conectan a MySQL.

Esta correccion se limita a la consulta individual de alumnos. No certifica la autorizacion de los demas endpoints ni el flujo completo del frontend.

## Validacion pendiente (2026-10-16)

Se reviso el diff y se intento ejecutar la suite con Java 17. El entorno rechazo acceso a archivos JAR (`java.nio.file.AccessDeniedException` en `WindowsPath.toRealPath`, al cerrar recursos del compilador), incluso usando una cache Maven nueva. La compilacion de pruebas no finalizo y no se ejecutaron los tests. No se declara BUILD SUCCESS. Ejecutar `./mvnw.cmd test` desde IntelliJ antes de integrar esta rama a una rama compartida.
