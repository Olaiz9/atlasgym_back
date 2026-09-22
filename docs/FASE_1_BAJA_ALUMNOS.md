# Primera correccion: baja de alumnos sin perder historial

## Regla acordada

Si un alumno paga junio y se da de baja en julio, el pago sigue en Finanzas de junio. La baja administrativa conserva la ficha y establece `activo=false`.

## Cambio

`Alumno.darDeBaja()` cambia exclusivamente el estado administrativo. Las relaciones con usuario, pagos, asistencias y sesiones mantienen cascadas PERSIST/MERGE, pero ya no REMOVE ni eliminacion de huerfanos. El callback `@PreRemove` rechaza un borrado fisico de Alumno mediante operaciones ordinarias JPA, incluso si no tiene historial.

No se agregan columnas ni se modifican registros de la base existente. Tampoco se ocultan globalmente los alumnos inactivos: eso haria desaparecer informacion de consultas historicas.

## Uso al implementar el servicio

Dentro de una transaccion, cargar el alumno, ejecutar `darDeBaja()` y persistir el cambio. No usar `delete`, `deleteById`, borrados en lote ni SQL DELETE para una baja administrativa. El callback no intercepta SQL directo ni borrados masivos JPQL; esas operaciones quedan fuera del flujo permitido. La proteccion no sustituye restricciones/permisos de base de datos.

Finanzas debe consultar pagos por periodo (o fecha de cobro, segun el reporte), sin filtrar por `alumno.activo`. El periodo pagado y el mes en que se recibe dinero pueden diferir; esta correccion no redefine esas reglas ni calcula nuevas cuotas.

La baja no cambia automaticamente el estado de Usuario. La politica de acceso de un exalumno se resolvera en autenticacion; conservar su cuenta y su historial no implica concederle permisos.

## Pruebas

`AlumnoHistorialPersistenceTest` usa JPA con H2 de prueba para verificar:

1. Tras la baja y recarga desde la base, el pago de junio sigue PAGADO y conserva importe, nombre y alumno; tambien quedan usuario, asistencia y sesion. Repetir la baja no cambia el historial.
2. Un intento de borrar Alumno con JPA es rechazado.
3. Vaciar las colecciones inversas no elimina registros historicos de la base.

`src/test/resources/application.properties` apunta solo a H2 efimero, aislando tambien el test de arranque existente de MySQL. H2 es una dependencia exclusiva de test. Ejecutar `mvnw.cmd test` desde el proyecto (o las pruebas Maven de IntelliJ). La validacion en MySQL queda para la integracion posterior.

Estado de verificacion en esta entrega: pruebas escritas, pero NO ejecutadas hasta completarse. El entorno de ejecucion rechazo acceso a archivos JAR durante la compilacion con Java 17 y Java 23 (`AccessDeniedException`) y tambien al intentar limpiar `target`. No se declara compilacion ni tests aprobados. Ejecutar la suite desde IntelliJ con el JDK 17 del proyecto antes de integrar esta correccion con otras ramas.

## Limite de esta entrega

Todavia no existen servicios/endpoints de alumnos o finanzas. Esta entrega protege el modelo y verifica la persistencia; el boton del frontend sigue pendiente de conectarse a la baja del backend. Los metodos de desvinculacion de hijos no deben usarse para dar de baja un alumno; su endurecimiento pertenece a la siguiente correccion de asociaciones.
