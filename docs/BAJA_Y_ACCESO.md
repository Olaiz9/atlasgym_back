# Baja administrativa y acceso

Regla acordada: un alumno dado de baja pierde acceso a su cuenta, incluso si conserva un JWT emitido antes de la baja. Sus pagos, asistencias y entrenamientos permanecen disponibles para administracion.

La baja del servicio ya desactivaba Alumno y Usuario. Ahora el filtro JWT comprueba el estado habilitado, bloqueo y vencimiento de la cuenta en cada solicitud antes de autenticarla. UserDetailsService consulta el estado actual de Usuario y de su ficha vinculada; el login tambien rechaza fichas inactivas aunque el usuario haya quedado activo en registros anteriores.

No cambia la base de datos ni se borran registros. El bloqueo se aplica a solicitudes posteriores a la confirmacion de la baja; no cancela una solicitud ya en curso ni borra datos que el navegador haya descargado. Las rutas publicas siguen siendo publicas. Una eventual reactivacion administrativa vuelve a habilitar el acceso segun los estados actuales; no se agrega una lista de revocacion permanente de tokens.

## Pruebas

BajaAlumnoAccesoTest agrega cuatro pruebas de integracion con H2, login y JWT reales:

- Acceso antes de la baja, rechazo del token y del login despues, conservando el pago y la cuenta.
- Bloqueo de una ficha inactiva cuyo usuario sigue activo.
- Bloqueo de un usuario inactivo con ficha activa.
- Acceso normal de un administrador sin ficha de alumno.

Tambien se comprueba que el token anterior no permita consultar el perfil, y que el login por DNI se rechace tras la baja.

Validacion de esta entrega: diff revisado. Maven se intento ejecutar con Java 17, pero el entorno fallo al cerrar recursos del compilador con acceso denegado a un JAR. Estas cuatro pruebas nuevas quedan pendientes de ejecutar desde IntelliJ con `./mvnw.cmd test`; se esperan 14 pruebas en total. Las 10 pruebas de la entrega anterior fueron aprobadas en el equipo del usuario.
