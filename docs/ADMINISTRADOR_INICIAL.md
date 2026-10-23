# Alta inicial del administrador

La cuenta existente de pruebas no se modifica. Esta entrega elimina la creacion automatica con una contrasena conocida y separa el alta inicial de administrador de los datos de demostracion.

## Uso diario en tu equipo

Si admin@atlasgym.com ya existe, podes seguir usando esa cuenta. No hace falta activar ninguna variable nueva. No se cambia su contrasena, rol ni estado. La clave conocida de una cuenta existente sigue siendo un riesgo si la base o la API se usan fuera del entorno de pruebas: debe reemplazarse antes de trabajar con datos reales o exponer el servicio.

## Base nueva sin administrador

Habilitar explicitamente el alta inicial en las variables de entorno de la configuracion de ejecucion de IntelliJ o del despliegue:

- ATLAS_ADMIN_BOOTSTRAP_ENABLED=true
- ATLAS_ADMIN_EMAIL: identificador elegido para el administrador.
- ATLAS_ADMIN_PASSWORD: contrasena nueva y exclusiva para ese entorno, de al menos 12 caracteres y como maximo 72 bytes UTF-8 (limite de BCrypt).

No usar el DNI, la clave antigua de demostracion ni frases predecibles. No guardar las credenciales en archivos versionados, capturas o documentos. La longitud minima no garantiza que una contrasena sea segura.

Para desarrollo local tambien se pueden agregar estas propiedades al archivo ignorado config/atlas-local.properties, manteniendo la clave JWT que ya contiene. Cada desarrollador configura su propia cuenta. No habilitar el alta inicial si ya se dispone de una cuenta administradora.

Arrancar el backend, confirmar el alta y acceder con las credenciales elegidas. Luego desactivar ATLAS_ADMIN_BOOTSTRAP_ENABLED y retirar ATLAS_ADMIN_PASSWORD de la configuracion; reiniciar para aplicar el cambio. La cuenta persistida sigue disponible.

## Comportamiento seguro del arranque

- El alta inicial esta desactivada por defecto, incluso si atlas.seed.enabled esta habilitado.
- Si se activa y faltan credenciales validas para una cuenta nueva, el arranque falla sin mostrar sus valores.
- La contrasena se guarda con BCrypt; no se imprime la contrasena ni el identificador configurados en el mensaje de alta.
- Un administrador existente se conserva: no se duplica, reactiva ni se le restablece la clave. La busqueda ignora mayusculas y minusculas.
- Si el identificador ya pertenece a una cuenta no administradora, se rechaza el alta: no se la convierte en ADMIN.

El alta inicial es una operacion de aprovisionamiento: ejecutarla en una sola instancia contra una base compartida. Deshabilitarla despues evita que una eliminacion posterior de la cuenta provoque su recreacion al reiniciar.

## Pruebas y alcance

AdminInitializerTest cubre alta deshabilitada, alta explicita con BCrypt y logs sin credenciales, claves invalidas, identificador ausente, preservacion de una cuenta administradora existente y rechazo de la conversion de un alumno en administrador.

Validacion del 2026-10-23: 38 pruebas aprobadas, sin fallos ni errores, incluyendo nueve casos nuevos. Suite ejecutada con Java 17 y el agente Byte Buddy precargado en el entorno del asistente.

Esta entrega no rota contrasenas existentes ni modifica el acceso offline del frontend, las claves iniciales de alumnos o la semilla de demostracion. ATLAS_SEED_ENABLED=false sigue siendo necesario en un despliegue real para desactivar esos datos, pero no es por si solo una configuracion completa de produccion.
