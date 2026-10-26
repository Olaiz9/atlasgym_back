# Configuracion de la clave JWT

La clave que antes estaba en application.properties fue publicada y debe considerarse comprometida. Esta entrega elimina su uso predeterminado. Cambiar el archivo no rota automaticamente servidores que ya estan ejecutandose: deben configurarse con una clave nueva y reiniciarse.

## Desarrollo local con IntelliJ

Desde la terminal de la raiz del backend, ejecutar una sola vez:

```powershell
.\scripts\configurar-jwt-local.ps1
```

El script genera 48 bytes aleatorios con el generador criptografico del sistema y guarda su representacion Base64 en `config/atlas-local.properties`. No muestra la clave ni sobrescribe un archivo existente. Git ignora este archivo; nunca forzar su agregado, compartirlo ni copiarlo en documentos.

Ejecutar BackendApplication en IntelliJ con Working directory igual a la raiz del backend (`$PROJECT_DIR$`). Spring importa automaticamente ese archivo desde el directorio de trabajo. No hace falta pegar el secreto en el codigo ni en una configuracion compartida de IntelliJ.

Cada desarrollador genera su propia clave. Si ATLAS_JWT_SECRET esta definida como variable de entorno, tiene prioridad sobre la propiedad del archivo local. En caso de usar una configuracion de ejecucion personalizada, revisar tambien sus variables y argumentos.

## Servidor

Configurar ATLAS_JWT_SECRET mediante el gestor de secretos o variables del entorno de despliegue. Usar una clave criptograficamente aleatoria distinta de la publicada, de las claves de pruebas y de las claves de otros entornos. No se incluye ningun secreto de ejemplo utilizable en produccion. Todas las instancias de un mismo entorno que verifican los mismos tokens deben usar la misma clave.

El backend rechaza el arranque si la clave esta ausente, en blanco o tiene menos de 32 bytes UTF-8. La validacion de longitud no demuestra aleatoriedad: no usar frases, contrasenas humanas ni repetir caracteres para cumplirla. El texto configurado se usa como bytes UTF-8, igual que antes; el formato Base64 del generador no se decodifica dentro de JwtService.

## Rotacion

Al sustituir la clave y reiniciar todas las instancias, los tokens anteriores dejan de verificarse. Los usuarios deben volver a iniciar sesion. El cambio no modifica contrasenas ni datos de alumnos.

La clave retirada sigue en el historial de Git: no volver a utilizarla. Esta entrega no reescribe el historial ni cambia automaticamente otros equipos o despliegues.

## Verificacion

`JwtConfiguracionTest` agrega ocho casos: cuatro entradas invalidas, rechazo del contexto sin clave, arranque con clave explicita, firma y lectura de un token, y rechazo de tokens tras rotacion. Las pruebas usan claves publicas exclusivas de test y no necesitan el archivo local. Ejecutar `./mvnw.cmd test` desde la raiz.

El administrador inicial, las contrasenas de alumnos, CORS y la configuracion de logs quedan para entregas separadas.

Validacion del 2026-10-23: suite completa aprobada con Java 17, 29 pruebas sin fallos ni errores. En el entorno del asistente fue necesario precargar el agente Byte Buddy mediante el argumento de JVM `-javaagent` para evitar el bloqueo al iniciar las pruebas con Mockito; no se cambia el pom ni la configuracion de ejecucion de la aplicacion.
