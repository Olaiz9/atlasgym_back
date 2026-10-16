# Privacidad de pagos

## Problema y correccion

Un ALUMNO podia cambiar el ID en GET /api/pagos/alumno/{alumnoId} y consultar los pagos de otro socio. Ahora el controlador resuelve la ficha desde el usuario autenticado y devuelve 403 antes de consultar pagos si el ID es ajeno, incluso cuando ese ID no existe. ADMIN conserva acceso a las fichas de todos los alumnos.

El listado general ya limitaba los pagos a la ficha del alumno conectado. La consulta individual por ID de pago, el registro, los cambios de estado (PATCH y PUT) y la eliminacion siguen reservados a ADMIN. No se amplian permisos ni se cambia el contrato de respuesta del frontend.

Las consultas administrativas siguen incluyendo pagos de alumnos inactivos. Esta entrega corrige autorizacion; no modifica importes, estados, reglas de cobro ni el borrado fisico de pagos que ya existe para administradores. La politica de anulacion y auditoria de pagos requiere una entrega separada.

## Pruebas

PagoAccesoTest agrega siete pruebas con dos alumnos, un administrador, H2 y JWT firmados por el servicio real. Se utilizan repositorios y servicios reales; no se prueba el formulario de login en esta clase.

- Listado general y por alumno limitados al titular, tambien al enviar mes.
- Rechazo de consultas de pagos ajenos.
- Rechazo del ID de alumno ajeno inexistente.
- Administrador consulta el periodo y los pagos de un alumno dado de baja.
- Alumno no crea, cambia estado ni borra pagos propios o ajenos; se comprueba que la base no cambio.
- Administrador conserva las operaciones de gestion existentes.
- Solicitudes anonimas no consultan ni gestionan pagos.

Se reviso el diff y se intento `mvn test` con Java 17. El entorno del asistente vuelve a fallar al cerrar recursos del compilador por acceso al JAR jul-to-slf4j. No se ejecutaron estas pruebas ni se declara BUILD SUCCESS. Ejecutar `./mvnw.cmd test` desde IntelliJ; se esperan 21 pruebas en la suite completa.
