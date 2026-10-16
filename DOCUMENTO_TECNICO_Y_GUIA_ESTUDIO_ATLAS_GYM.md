# ATLAS GYM — MANUAL TÉCNICO DE ARQUITECTURA Y GUÍA MAESTRA DE ESTUDIO
## De Cero a Producción: Arquitectura, Código y Reglas de Negocio en un Proyecto de Software Real

**Equipo de Desarrollo:** Lucas · Bruno · Augusto  
**Proyecto:** Plataforma Integral de Gestión y Entrenamiento ATLAS Gym  
**Tecnologías:** Next.js · TypeScript · Tailwind CSS · Spring Boot 3.4.3 · Java 17 · Spring Security · JWT · Spring Data JPA · MySQL 8  
**Fecha:** Octubre 2026  
**Versión del Documento:** 1.0 — Entrega de Arquitectura y Guía Formativa

---

## ÍNDICE GENERAL

1. [Prólogo: La Filosofía de este Documento](#1-prólogo-la-filosofía-de-este-documento)
2. [Visión General de la Arquitectura](#2-visión-general-de-la-arquitectura)
3. [Fundamentos Cruciales de Estudio: Desde Cero Absoluto](#3-fundamentos-cruciales-de-estudio-desde-cero-absoluto)
   * 3.1. ¿Cómo funciona la Web? El Protocolo HTTP
   * 3.2. Formato JSON: La lengua franca
   * 3.3. Stateless (Sin Estado): Por qué no usamos sesiones de servidor
   * 3.4. CORS: El policía de seguridad del navegador
   * 3.5. Variables de entorno y portabilidad
4. [El Ecosistema Frontend: Next.js, React y TypeScript](#4-el-ecosistema-frontend-nextjs-react-y-typescript)
   * 4.1. Por qué Next.js sobre React simple
   * 4.2. TypeScript y la seguridad de tipos
   * 4.3. El patrón de Estado Centralizado (`lib/store.tsx`)
   * 4.4. Testing automatizado con Vitest (114 tests)
5. [El Ecosistema Backend: Spring Boot, Java 17 y MySQL](#5-el-ecosistema-backend-spring-boot-java-17-y-mysql)
   * 5.1. La Arquitectura en Capas Estricta (Separación de Responsabilidades)
   * 5.2. Inyección de Dependencias y Principio IoC
   * 5.3. JPA y Hibernate: El Mapeo Objeto-Relacional (ORM)
   * 5.4. DTOs (Records de Java 17): Por qué NUNCA exponer Entidades
   * 5.5. `@Transactional`: Integridad ACID y Rollback
   * 5.6. Manejo Global de Errores con `@RestControllerAdvice`
6. [Seguridad y Autenticación Moderna](#6-seguridad-y-autenticación-moderna)
   * 6.1. Hashing de Contraseñas con BCrypt
   * 6.2. JSON Web Tokens (JWT) en profundidad
   * 6.3. Cadena de Filtros de Spring Security (`OncePerRequestFilter`)
   * 6.4. Control de Acceso Basado en Roles (RBAC)
7. [Las 7 Reglas de Negocio Críticas de ATLAS Gym](#7-las-7-reglas-de-negocio-críticas-de-atlas-gym)
   * 7.1. Regla Anti-fraude del Tótem (100 minutos)
   * 7.2. Snapshot Inmutable de Pagos
   * 7.3. Baja Administrativa Lógica (`@PreRemove`)
   * 7.4. Estado de Cuenta Dinámico
   * 7.5. Protección de Planes Activos
   * 7.6. Propagación de Rutinas por Clave Foránea
   * 7.7. Workout Tracker y Sobrecarga Progresiva
8. [Mapa de Endpoints y Servicios del Backend](#8-mapa-de-endpoints-y-servicios-del-backend)
9. [Cómo se Conectan el Frontend y el Backend](#9-cómo-se-conectan-el-frontend-y-el-backend)
10. [Plan de Estudio y Preparación para el Equipo](#10-plan-de-estudio-y-preparación-para-el-equipo)

---

## 1. Prólogo: La Filosofía de este Documento

Este proyecto tiene una importancia doble para el equipo:
1. **Es su primer trabajo profesional remunerado:** representa el paso del mundo académico al mercado laboral real, donde el código debe ser robusto, seguro, mantenible y cumplir requerimientos de un negocio real.
2. **Es un laboratorio de aprendizaje intensivo:** el software no se "arma y se entrega a ciegas". Un verdadero ingeniero de software no entrega nada sin saber exactamente qué hace cada línea, por qué se eligió cada librería y cómo interactúan las capas entre sí.

Este manual fue redactado para que **Lucas, Bruno y Augusto** puedan sentarse juntos, estudiar cada concepto desde lo más básico hasta lo más avanzado, y entender con claridad total el sistema que construyeron.

---

## 2. Visión General de la Arquitectura

El sistema ATLAS Gym utiliza el patrón **MVC Desacoplado (API REST)**:

```
[ FRONTEND ]                            [ BACKEND ]                         [ BASE DE DATOS ]
Next.js (React + TS)                   Spring Boot 3.4.3 (Java 17)          MySQL 8 (Puerto 3307)
Puerto 3000                            Puerto 8080
--------------------                   ---------------------------          ---------------------
• Renderizado de interfaz              • Controlador (REST API)
• Estado local (Store)       HTTP      • Seguridad (JWT + BCrypt)    JDBC    • Tablas relacionales
• Validación de UX       ===========>  • Servicios (Lógica Negocio) ======>  • Claves foráneas (FK)
• Llamadas API (fetch)   <===========  • Repositorios (JPA/JPQL)    <======  • Índices optimizados
                         JSON Payload  • Manejador de Excepciones           • Integridad referencial
```

### ¿Por qué esta arquitectura y no un monolito tradicional?
1. **Independencia de despliegue:** el frontend de Next.js puede actualizarse o desplegarse en Vercel sin reiniciar el servidor de Spring Boot, y viceversa.
2. **Especialización:** el frontend se concentra en la experiencia de usuario (diseño responsivo, animaciones, rapidez); el backend se concentra en la seguridad, las transacciones financieras y la integridad de los datos.
3. **Escalabilidad:** si mañana ATLAS Gym quiere lanzar una app nativa para Android/iOS o un Tótem físico en Raspberry Pi, **usan exactamente la misma API REST del backend sin tocar una sola línea de Java**.

---

## 3. Fundamentos Cruciales de Estudio: Desde Cero Absoluto

### 3.1. ¿Cómo funciona la Web? El Protocolo HTTP
Toda la comunicación web se basa en el protocolo **HTTP (Hypertext Transfer Protocol)**, un modelo de petición-respuesta (*Request-Response*):
* **El Cliente (Next.js)** envía un paquete de texto llamado **Request**.
* **El Servidor (Spring Boot)** procesa la petición y devuelve un paquete de texto llamado **Response**.

#### Los Métodos HTTP (Verbos)
Cada petición debe declarar su intención:
* **`GET`**: "Quiero leer datos". No debe modificar nada en el servidor (es *idempotente*). Ejemplo: `GET /api/alumnos`.
* **`POST`**: "Quiero crear un nuevo recurso". Envía datos en el cuerpo (*Body*). Ejemplo: `POST /api/alumnos`.
* **`PUT`**: "Quiero actualizar o reemplazar un recurso existente". Ejemplo: `PUT /api/alumnos/5`.
* **`DELETE`**: "Quiero dar de baja o eliminar un recurso". Ejemplo: `DELETE /api/alumnos/5`.

#### Los Códigos de Estado HTTP (Status Codes)
Indican el resultado de la operación:
* **`200 OK`**: La consulta se ejecutó con éxito y se devuelven datos.
* **`201 Created`**: El recurso se creó exitosamente (usado en `POST /api/alumnos` o `POST /api/pagos`).
* **`204 No Content`**: La operación tuvo éxito, pero no hay contenido para devolver (ideal para `DELETE` y bajas lógicas).
* **`400 Bad Request`**: La petición está mal armada (faltan campos obligatorios, DNI con formato erróneo).
* **`401 Unauthorized`**: No presentaste credenciales o tu token JWT expiró.
* **`403 Forbidden`**: El servidor sabe quién sos, pero no tenés permiso (ej. un Alumno queriendo ver la facturación del Admin).
* **`404 Not Found`**: El recurso no existe en la base de datos (ej. Alumno ID 999).
* **`409 Conflict`**: Conflicto con el estado actual de la base (ej. DNI ya registrado o eliminar un plan con alumnos activos).
* **`500 Internal Server Error`**: Ocurrió un error no controlado en el backend (un bug que debe solucionarse).

---

### 3.2. Formato JSON: La lengua franca
**JSON (JavaScript Object Notation)** es un formato de texto estructurado en pares clave-valor. Es el estándar mundial porque es liviano y cualquier lenguaje (Java, JavaScript, Python, Go) puede parsearlo sin esfuerzo.

```json
{
  "id": 1,
  "nombre": "Lucas Pérez",
  "dni": "42123456",
  "activo": true,
  "plan": {
    "nombre": "Plan Musculación Libre",
    "precio": 22000.00
  }
}
```

---

### 3.3. Stateless (Sin Estado): Por qué no usamos sesiones de servidor
En aplicaciones antiguas, el servidor guardaba un objeto en memoria (`HttpSession`) para recordar quién estaba conectado.
* **El problema:** si el servidor se reinicia, todos los usuarios son deslogueados. Si hay dos servidores, el servidor B no sabe lo que guardó el servidor A.
* **La solución moderna (Stateless):** el servidor no guarda nada en su memoria RAM sobre la sesión. Cada petición que hace el cliente incluye en sus Headers un pase digital criptográfico (**el Token JWT**). El servidor lee el token, verifica la firma matemática y responde en milisegundos.

---

### 3.4. CORS: El policía de seguridad del navegador
**CORS (Cross-Origin Resource Sharing)** es un mecanismo de seguridad implementado por **los navegadores web** (Chrome, Firefox, Edge).
* Si una página que corre en `http://localhost:3000` intenta hacer un `fetch()` a `http://localhost:8080`, el navegador bloquea la llamada a menos que el servidor en el puerto 8080 diga explícitamente:  
  *`"Autorizo a http://localhost:3000 a hacerme peticiones con credenciales"`.*
* En nuestro backend, esto lo resolvimos en [`SecurityConfig.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Seguridad/SecurityConfig.java) mediante el bean `CorsConfigurationSource`.

---

### 3.5. Variables de entorno y portabilidad
Nunca se debe "quemar" (*hardcodear*) una contraseña, URL de base de datos o clave secreta en el código.
En `application.properties` usamos la sintaxis:
```properties
spring.datasource.url=jdbc:mysql://localhost:${DB_PORT:3307}/atlas_gym
```
Esto significa: *"Buscá la variable de entorno `DB_PORT`. Si existe, usala; si no existe, usá el valor por defecto 3307"*. Así, cualquier miembro del equipo puede clonar el proyecto y correrlo sin modificar código Java.

---

## 4. El Ecosistema Frontend: Next.js, React y TypeScript

### 4.1. Por qué Next.js sobre React simple
React puro (creado con Vite o CRA) renderiza todo en el navegador del cliente mediante un archivo HTML vacío y un archivo JavaScript pesado.
**Next.js** aporta:
1. **Enrutamiento por sistema de archivos:** crear `app/alumnos/page.tsx` genera automáticamente la ruta `/alumnos`.
2. **Optimización de recursos:** componentes como `<Image />` comprimen y sirven imágenes automáticamente en formato moderno (WebP) adaptado a la pantalla.
3. **División de componentes:** Server Components (rápidos, se ejecutan en Node.js) y Client Components (`"use client"`, interactivos con botones y formularios).

### 4.2. TypeScript y la seguridad de tipos
JavaScript tradicional no avisa de errores hasta que el código explota en el navegador del usuario. TypeScript agrega un sistema de tipos en tiempo de compilación:
* Si definimos que `Alumno` tiene un campo `id: string` y en una pantalla escribimos `alumno.codigo`, el editor nos marca el error antes de guardar.
* Define contratos claros en `lib/types.ts` que coinciden exactamente con los DTOs del backend.

### 4.3. El patrón de Estado Centralizado (`lib/store.tsx`)
En React, si una pantalla (`/alumnos`) tiene sus propios datos y otra pantalla (`/finanzas`) tiene los suyos, agregar un socio en Alumnos no actualizaría la lista en Finanzas.
* Bruno implementó el patrón **Context + Custom Hook (`useAppData`)**.
* `lib/store.tsx` actúa como una **única fuente de verdad** compartida por todos los componentes.
* La ventaja estratégica: cuando conectamos el backend, no hay que tocar los 20 componentes de las páginas. **Solo se reemplaza la lectura de los arrays locales en el store por llamadas HTTP**.

### 4.4. Testing automatizado con Vitest
El frontend cuenta con una suite de **114 pruebas unitarias y de integración** que garantizan que las reglas críticas (cálculo de cuotas vencidas, anti-fraude, consistencia de planes) no se rompan ante cambios futuros.

---

## 5. El Ecosistema Backend: Spring Boot, Java 17 y MySQL

### 5.1. La Arquitectura en Capas Estricta

```
[ Request HTTP de Next.js ]
           │
           ▼
1. Controlador (@RestController)    ──> Recibe el JSON, valida sintaxis (@Valid), mapea HTTP status.
           │
           ▼
2. Servicio (@Service)              ──> El CEREBRO. Aplica reglas de negocio, transacciones (@Transactional).
           │
           ▼
3. Repositorio (@Repository)        ──> Consulta la base de datos vía Spring Data JPA y JPQL.
           │
           ▼
4. Base de Datos (MySQL)            ──> Persiste las tablas y asegura integridad referencial.
```

### 5.2. Inyección de Dependencias (IoC)
En Java tradicional, si una clase necesita otra, hace `new AlumnoService()`. Esto genera código acoplado e imposible de testear.
Con Spring Boot, **el contenedor de Spring crea y gestiona las instancias** (llamadas *Beans*):
```java
@Service
public class AlumnoService {
    private final AlumnoRepository alumnoRepository;

    // Spring busca automáticamente el bean de AlumnoRepository y lo inyecta aquí:
    public AlumnoService(AlumnoRepository alumnoRepository) {
        this.alumnoRepository = alumnoRepository;
    }
}
```

### 5.3. JPA y Hibernate: El Mapeo Objeto-Relacional (ORM)
En Java trabajamos con **Objetos** (clases, listas, herencia); en MySQL trabajamos con **Tablas** (filas, columnas, claves foráneas).
**JPA (Java Persistence API)** es la especificación y **Hibernate** es el motor que traduce automáticamente nuestros objetos Java a sentencias SQL.

#### Mapeos Clave:
* `@Entity` y `@Table(name = "alumnos")`: Marca la clase como tabla en la base de datos.
* `@Id` y `@GeneratedValue(strategy = GenerationType.IDENTITY)`: Clave primaria autoincremental de MySQL.
* `@ManyToOne` / `@OneToMany`: Relación 1 a N. Por ejemplo: 1 `Plan` tiene muchos `Alumnos`.
* `cascade = CascadeType.ALL`: Si guardo una `Rutina`, Hibernate automáticamente persiste todos sus `DiaRutina` y `Ejercicio` asociados.
* `orphanRemoval = true`: Si saco un ejercicio de la lista en Java, Hibernate ejecuta un `DELETE` automático de esa fila huérfana en MySQL.

---

### 5.4. DTOs (Records de Java 17): Por qué NUNCA exponer Entidades
Una regla de oro de la ingeniería de software es: **jamás devolver una `@Entity` de JPA en un `@RestController`**.
* **Problema 1 (Ciclos infinitos):** Si `Alumno` tiene `List<Pago>` y `Pago` tiene `Alumno`, Jackson (el serializador a JSON) entra en un bucle infinito y desborda la memoria (`StackOverflowError`).
* **Problema 2 (Fuga de seguridad):** La entidad `Usuario` contiene la contraseña hasheada. Si la devolvés en el JSON, estás exponiendo hashes al navegador.
* **Problema 3 (Over-fetching):** Traer datos innecesarios hace que la app móvil o web sea lenta.

**Solución:** Los **DTOs (Data Transfer Objects)**. En Java 17 los implementamos con `record`:
```java
public record AlumnoResponseDto(
    Long id,
    String nombre,
    String dni,
    String email,
    String planNombre,
    EstadoCuenta estadoCuenta
) {}
```
Los records son inmutables, compactos (generan constructor, getters, `equals`, `hashCode` y `toString` automáticamente) y garantizan contratos de datos limpios.

---

### 5.5. `@Transactional`: Integridad ACID y Rollback
Cuando un socio registra un pago o termina una sesión de entrenamiento, ocurren múltiples escrituras en distintas tablas.
Si colocamos `@Transactional` sobre el método del servicio:
1. Spring abre una transacción en MySQL (`START TRANSACTION`).
2. Se ejecutan todas las operaciones.
3. Si ocurre un error o salta una excepción en cualquier línea, **Spring ejecuta automáticamente `ROLLBACK`**.
4. La base de datos queda exactamente como estaba antes de empezar. Nada queda a medio guardar.

---

### 5.6. Manejo Global de Errores con `@RestControllerAdvice`
En lugar de llenar cada método del controlador con bloques `try-catch`, creamos [`GlobalExceptionHandler.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Excepciones/GlobalExceptionHandler.java).
Cualquier excepción arrojada en cualquier servicio (`ResourceNotFoundException`, `BusinessException`, error de validación) sube en la pila y es atrapada por este interceptor, devolviendo un JSON uniforme con código de estado HTTP adecuado.

---

## 6. Seguridad y Autenticación Moderna

### 6.1. Hashing de Contraseñas con BCrypt
Las contraseñas **NUNCA** se guardan en texto plano en la base de datos. Si un atacante roba un backup, tendría las claves de todos los usuarios.
* Tampoco se usan algoritmos como MD5 o SHA-256 solos (porque hoy en día se descifran en segundos con tablas Rainbow o GPUs potentes).
* Usamos **BCrypt** (`BCryptPasswordEncoder`):
  * Es una función de derivación de claves que incluye un **Salt** aleatorio (evita ataques por diccionario).
  * Es intencionalmente costosa computacionalmente para que un ataque de fuerza bruta tarde décadas.
  * Es una función unidireccional: **nadie puede "desencriptar" una contraseña de BCrypt**. Para verificar el login, el backend hashea la contraseña enviada por el usuario con el mismo Salt y compara si ambos hashes coinciden (`passwordEncoder.matches(plana, hasheada)`).

---

### 6.2. JSON Web Tokens (JWT) en Profundidad
El JWT es una cadena de texto dividida en 3 partes separadas por puntos (`.`), codificadas en Base64Url:

$$\text{JWT} = \underbrace{\text{Header}}_{\text{Algoritmo}} \ .\ \underbrace{\text{Payload}}_{\text{Datos/Claims}} \ .\ \underbrace{\text{Signature}}_{\text{Firma Secreta}}$$

1. **Header:** indica el algoritmo de firma (en nuestro caso, `HS256` = HMAC con SHA-256).
2. **Payload (Claims):** los datos útiles que viajan adentro:
   * `sub` (Subject): email del usuario.
   * `id`: ID único de usuario en MySQL.
   * `rol`: `"ADMIN"` o `"ALUMNO"`.
   * `alumnoId`: ID de la ficha de alumno (si corresponde).
   * `exp`: Fecha y hora exacta de expiración (24 horas).
3. **Signature:** el servidor genera la firma combinando el Header, el Payload y una clave secreta (`atlas.jwt.secret`) que solo conoce el backend.

> [!IMPORTANT]
> Si un usuario modifica su rol en el Payload de `"ALUMNO"` a `"ADMIN"`, la firma matemática deja de coincidir instantáneamente. El backend detecta la manipulación y rechaza la petición con HTTP 403.

---

### 6.3. Cadena de Filtros de Spring Security
Cada petición HTTP que llega a Spring Boot pasa por una cadena de filtros antes de llegar a los controladores:
1. Pasa por el filtro de CORS (verifica el origen del navegador).
2. Pasa por nuestro [`JwtAuthenticationFilter.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Seguridad/JwtAuthenticationFilter.java):
   * Lee el header `Authorization: Bearer <token>`.
   * Valida la firma y fecha de expiración con `JwtService`.
   * Si es válido, carga los roles del usuario en el `SecurityContextHolder` de Spring.
3. Si la ruta requiere `ADMIN` y el usuario es `ALUMNO`, Spring bloquea el paso antes de ejecutar el controlador.

---

## 7. Las 7 Reglas de Negocio Críticas de ATLAS Gym

### 7.1. Regla Anti-fraude del Tótem (100 minutos)
* **El problema de negocio:** socios que digitan su DNI en el Kiosco de ingreso y luego le prestan el DNI a un compañero que viene detrás para que entre gratis.
* **La solución en código:** en [`AsistenciaService.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Servicio/AsistenciaService.java), al digitar el DNI, se consulta el último ingreso de hoy. Si la diferencia es menor a 100 minutos, el sistema rechaza la entrada informando: *"Tu sesión continúa activa"*. Si pasaron 100 minutos o más (por ejemplo, alguien que entrena a la mañana y vuelve a la tarde), se registra una nueva entrada legítima.

### 7.2. Snapshot Inmutable de Pagos
* **El problema de negocio:** el gimnasio actualiza el precio del plan de \$20.000 a \$28.000 en noviembre. Si un recibo de octubre consultaba el precio haciendo un `JOIN` a la tabla `planes`, el recibo histórico pasaría a mostrar \$28.000, falseando el balance contable pasado.
* **La solución en código:** en [`PagoService.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Servicio/PagoService.java), cada pago guarda una copia fija en texto de `alumnoNombreHistorico` y `monto`. Aunque el socio cambie de apellido o el plan suba de precio, el recibo original jamás se altera.

### 7.3. Baja Administrativa Lógica (`@PreRemove`)
* **El problema de negocio:** un socio deja de pagar y se da de baja. Si se ejecuta un `DELETE FROM alumnos`, se borran en cascada sus asistencias históricas, sus pagos y sus récords de levantamiento, arruinando las estadísticas del gimnasio.
* **La solución en código:** en [`Alumno.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Modelo/Alumno.java), el método `darDeBaja()` pone `activo = false`. Además, la anotación `@PreRemove` arroja una `IllegalStateException` bloqueando cualquier intento de borrado físico directo en la base.

### 7.4. Estado de Cuenta Dinámico
* **El problema de negocio:** si guardás una columna `estado = "AL_DIA"` en la base de datos, el día 1 del mes siguiente tendrías que correr un script masivo para pasar a todos los alumnos a morosos, sobrecargando el servidor.
* **La solución en código:** el estado se calcula **al vuelo** al momento de mapear el DTO:
  * `INACTIVO`: Si `activo == false` o no registra pagos en más de 60 días.
  * `AL_DIA`: Si existe un pago registrado para el período `YYYY-MM` actual.
  * `PENDIENTE`: Si no pagó el mes actual pero estamos dentro de los primeros 10 días del mes (período de gracia).
  * `MOROSO`: Si no pagó el mes actual y ya es día 11 en adelante.

### 7.5. Protección de Planes Activos
* **La solución en código:** antes de eliminar un plan, [`PlanService.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Servicio/PlanService.java) consulta si existen alumnos activos asociados. Si hay al menos uno, cancela la operación arrojando `BusinessException` con código **HTTP 409 Conflict**.

### 7.6. Propagación de Rutinas por Clave Foránea
* **La solución en código:** los alumnos apuntan a una `Rutina` mediante clave foránea (`rutina_id`). Cuando un profesor modifica ejercicios o series de una rutina, Hibernate actualiza la entidad `Rutina`. Al instante siguiente, todos los alumnos que abren la app ven los cambios actualizados sin necesidad de sincronizar alumno por alumno.

### 7.7. Workout Tracker y Sobrecarga Progresiva
* **La solución en código:** en [`SesionEntrenamientoService.java`](file:///C:/Users/Lucas/OneDrive/Documentos/IntelliJ/atlasgym_back/src/main/java/com/atlas/backend/Servicio/SesionEntrenamientoService.java), el endpoint `GET /api/sesiones/ultima?alumnoId=X&diaRutinaId=Y` busca el entrenamiento previo más reciente de ese mismo día de rutina, permitiendo al socio ver qué peso levantó la semana pasada para intentar superarlo hoy.

---

## 8. Mapa de Endpoints y Servicios del Backend

| Módulo | Endpoint | Método | Acceso | Descripción |
|---|---|---|---|---|
| **Autenticación** | `/api/auth/login` | `POST` | Público | Autenticación real por email/contraseña; devuelve JWT |
| | `/api/auth/perfil` | `GET` | Autenticado | Devuelve los datos del usuario en sesión |
| **Alumnos** | `/api/alumnos` | `GET` | `ADMIN` | Lista todos los socios activos |
| | `/api/alumnos/inactivos` | `GET` | `ADMIN` | Lista socios dados de baja |
| | `/api/alumnos/{id}` | `GET` | `ADMIN` | Ficha detallada de un socio |
| | `/api/alumnos/buscar` | `GET` | `ADMIN` | Búsqueda por DNI o por nombre |
| | `/api/alumnos` | `POST` | `ADMIN` | Alta de nuevo socio |
| | `/api/alumnos/{id}` | `PUT` | `ADMIN` | Modificación de datos |
| | `/api/alumnos/{id}` | `DELETE` | `ADMIN` | Baja administrativa lógica (`activo=false`) |
| **Asistencias** | `/api/asistencias/totem` | `POST` | Público | Check-in en el Kiosco por DNI (regla 100 min) |
| | `/api/asistencias/aforo` | `GET` | `ADMIN` | Aforo en tiempo real en la sala |
| | `/api/asistencias` | `GET` | `ADMIN` | Historial de ingresos por fecha |
| | `/api/asistencias/distribucion`| `GET` | `ADMIN` | Gráfico de distribución de ingresos por hora |
| **Pagos** | `/api/pagos` | `POST` | `ADMIN` | Registro de cobro de cuota |
| | `/api/pagos` | `GET` | `ADMIN` | Lista de pagos (filtro por mes `?mes=YYYY-MM`) |
| | `/api/pagos/alumno/{id}` | `GET` | `ADMIN`, `ALUMNO` | Historial de pagos de un socio |
| **Planes** | `/api/planes` | `GET` | Autenticado | Planes de membresía activos |
| | `/api/planes` | `POST` | `ADMIN` | Crear nuevo plan |
| | `/api/planes/{id}` | `DELETE` | `ADMIN` | Eliminar plan (bloqueado si tiene socios) |
| **Rutinas** | `/api/rutinas/genericas` | `GET` | Autenticado | Catálogo de rutinas base del gimnasio |
| | `/api/rutinas/mis-rutinas`| `GET` | `ALUMNO` | Rutina asignada al alumno autenticado |
| | `/api/rutinas` | `POST` | `ADMIN` | Crear rutina con días y ejercicios |
| | `/api/rutinas/{rId}/asignar/{aId}` | `POST` | `ADMIN` | Asignar rutina a la ficha de un alumno |
| **Sesiones** | `/api/sesiones` | `POST` | `ALUMNO`, `ADMIN` | Guardar entrenamiento completado del tracker |
| | `/api/sesiones/ultima` | `GET` | `ALUMNO`, `ADMIN` | Pesos de la sesión previa para sobrecarga progresiva |
| **Avisos** | `/api/avisos/mis-avisos` | `GET` | `ALUMNO` | Comunicados institucionales y privados |
| | `/api/avisos/{id}/leido` | `PUT` | `ALUMNO` | Marcar aviso como leído |
| **Videoteca** | `/api/videoteca` | `GET` | Autenticado | Catálogo de videos de técnica |
| | `/api/videoteca/grupo/{grupo}` | `GET` | Autenticado | Filtrar ejercicios por grupo muscular |

---

## 9. Cómo se Conectan el Frontend y el Backend

La conexión se realiza en tres pasos:

1. **Cliente HTTP Centralizado (`lib/api.ts`):**  
   Una función envoltorio de `fetch()` que añade la URL base (`http://localhost:8080/api`) y recupera el token JWT guardado en `localStorage` para inyectarlo en el encabezado `Authorization: Bearer <token>`.
2. **Pantalla de Login (`app/login/page.tsx`):**  
   Reemplaza los datos mock locales por la llamada a `POST /api/auth/login`. Si el backend responde 200 OK, guarda el token y redirige a la pantalla principal.
3. **Store Global (`lib/store.tsx`):**  
   Reemplaza la lectura de los arrays estáticos (`ALUMNOS_MOCK`, `PAGOS_MOCK`) por llamadas a los endpoints REST correspondientes.

### Credenciales Reales Disponibles en la Base de Datos:
* **Administrador:** `admin@atlasgym.com` / `admin123`
* **Alumno:** `alumno@atlasgym.com` / `alumno123` (vinculado al socio Lucas Pérez, DNI 42123456)

---

## 10. Plan de Estudio y Preparación para el Equipo

Para dominar este proyecto y defenderlo con nivel profesional ante clientes, profesores o entrevistas laborales, se recomienda el siguiente orden de estudio:

```
PASO 1: Los Fundamentos
• ¿Qué es una API REST y por qué se usan códigos HTTP 200, 201, 204, 400, 401, 403, 404, 409?
• ¿Qué es JSON y por qué la arquitectura es Stateless?
• ¿Qué es CORS y cómo lo resuelve Spring Security?

PASO 2: La Lógica y la Base de Datos
• ¿Cómo funciona el mapeo relacional en JPA/Hibernate (@ManyToOne, cascade, orphanRemoval)?
• ¿Por qué usamos @Transactional y qué significa el principio ACID?
• ¿Por qué nunca devolvemos Entidades en un Controller y usamos DTOs (Records de Java 17)?
• Las 7 reglas de negocio de ATLAS Gym (Anti-fraude 100 min, snapshot de pago, baja lógica).

PASO 3: La Seguridad
• ¿Cómo funciona el hashing con BCrypt y por qué no se puede desencriptar?
• ¿Cómo se estructura un JWT (Header, Payload, Signature) y cómo lo valida el filtro de Spring?
• ¿Cómo se configuran los roles con @PreAuthorize("hasRole('ADMIN')")?

PASO 4: El Frontend
• ¿Por qué Bruno diseñó el estado en lib/store.tsx con Context y Custom Hooks?
• ¿Cómo viaja el token JWT en el header Authorization de cada fetch?
• ¿Cómo se manejan los errores de validación que llegan desde el GlobalExceptionHandler?
```

---
*Fin del Documento Técnico y Guía de Estudio — ATLAS Gym Backend & Frontend Architecture.*
