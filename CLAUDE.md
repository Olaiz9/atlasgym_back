# ATLAS Gym — Backend (Spring Boot 3.4.3 / Java 17+ / MySQL 8.0)

> **Reglas de trabajo para todas las IAs (Git, tests, migraciones, alcance): ver `AGENTS.md`.**
> Este archivo es el contexto de negocio y arquitectura.
## Guía de Arquitectura, Reglas de Negocio y Operación para Claude Code

Este archivo proporciona el contexto completo y las reglas operativas de ingeniería para cualquier sesión de desarrollo, auditoría o mantenimiento con **Claude Code** en este repositorio.

---

### 1. Contexto del Negocio y del Cliente Real (Gimnasio ATLAS)

El software gestiona un gimnasio comercial real en Argentina próximo a su entrega en producción:
- **Operadores del sistema:** La aplicación web administrativa es operada exclusivamente por **dos hermanos (dueños/titulares)** desde sus teléfonos y notebooks, más **1 tablet física en el mostrador** funcionando como Tótem de auto-checkin. No existen recepcionistas empleados terceros (`Rol.ADMIN` y `Rol.ALUMNO`).
- **Rendición de Caja:** Ambos hermanos cobran cuotas en el mostrador. Cada cobro, seña o anulación registra nominalmente qué titular operó (`registradoPor`, `anuladoPor`) para que el **Arqueo de Caja del Día** separe exactamente lo recaudado por cada uno y por método (Efectivo, Transferencia, Tarjeta).
- **Máquina de Estados de Cuotas (Regla Acordada "Opción A"):**
  - Del día 1 al 10 del mes: Cuota al día (`AL_DIA`).
  - Del día 11 al último día del mes: Cuota pendiente (`PENDIENTE`).
  - Mes siguiente sin abonar: Socio moroso (`MOROSO`).
  - Más de 60 días sin pagar o socio dado de baja: `INACTIVO`.
  - Soporta pagos de múltiples meses adelantados (`cantidadMeses >= 1`).
- **Control de Asistencia y Límites Semanales:**
  - El Tótem **nunca bloquea el ingreso físico** de un socio en el molinete/puerta.
  - Si un socio excede los días de su plan en la semana ISO (Lunes a Domingo), el sistema registra la asistencia y genera una alerta visual destacada en la pantalla de `/asistencias` para que el titular a cargo converse con el socio.
- **Tótem Resiliente y Modo Offline:**
  - La tablet física se vincula una sola vez con un secreto (`X-Totem-Secret`), el cual valida el padrón mínimo en caché (`GET /api/asistencias/totem/padron-cache`).
  - Ante cortes de Internet en el local, la tablet guarda los ingresos en una cola Outbox local y los sincroniza de forma idempotente (`POST /api/asistencias/totem/sync`) mediante UUIDs únicos (`clientEventId`).

---

### 2. Stack Tecnológico

- **Framework:** Spring Boot 3.4.3 con Java 17+ (testeado con Java 24).
- **ORM / Persistencia:** Spring Data JPA / Hibernate 6.
- **Base de Datos:** MySQL 8.0 (charset `utf8mb4`, collation `utf8mb4_unicode_ci`, time zone `-03:00`).
- **Migraciones Versionadas:** Flyway 10 (con `spring.jpa.hibernate.ddl-auto=validate` forzado en producción).
- **Seguridad:** Spring Security 6 + JJWT 0.12.3 (HMAC-SHA256, expiración 24 hs).
- **Infraestructura:** Docker Compose + Caddy 2 (reverse proxy) detrás de un túnel de Cloudflare (`cloudflared`, HTTPS en Cloudflare) + Scripts de backup/restore.

---

### 3. Migraciones de Base de Datos (Flyway)

Las migraciones residen en `src/main/resources/db/migration/`:
- `V1__esquema_inicial.sql`: Tablas base (`usuarios`, `planes`, `alumnos`, `pagos`, `asistencias`, `rutinas`, `ejercicios`, `avisos`, `videos_tecnica`, `sesiones_entrenamiento`).
- `V2__totem_outbox_e_indices.sql`: Índices de rendimiento y soporte para sincronización offline del tótem (`client_event_id VARCHAR(64) UNIQUE`).
- `V3__hardening_identidad.sql`: Columna `requiere_cambio_password BOOLEAN` y `nombre_mostrar VARCHAR(100)` en `usuarios`.
- `V4__caja_real_argentina.sql`: Columnas para caja real: `monto_abonado DECIMAL(10,2)`, `registrado_por VARCHAR(100)`, `anulado_por VARCHAR(100)`, `fecha_anulacion DATETIME(6)`, `motivo_anulacion VARCHAR(255)`, e índice `idx_pagos_fecha_estado`.
- `V5__movimientos_caja.sql`: Tabla `movimientos_caja`: cada ingreso (pago total, seña, saldo, ajuste) con su propio día, método y titular. El arqueo diario se calcula sumando movimientos, no el estado final del comprobante.
- `V6__configuracion_cobro.sql`: Tabla `configuracion_cobro` (fila única) con alias/titular de transferencia editables por los titulares.
- `V7__totem_latido.sql`: Tabla `totem_latido` (fila única) con la última señal de vida de la tablet del Tótem y cuántos ingresos tenía sin subir.

> [!CRITICAL]
> **Regla de Esquema Hibernate:** En perfil `prod`, `spring.jpa.hibernate.ddl-auto` está seteado en `validate`. NUNCA modifiques una entidad JPA sin crear la migración Flyway correspondiente (`V7__...sql`, la siguiente versión libre), de lo contrario la aplicación fallará al arrancar.

---

### 4. Endpoints Clave y Contratos de API

- **Autenticación (`/api/auth`):**
  - `POST /api/auth/login`: Autentica email/DNI y contraseña. Devuelve JWT y objeto `LoginResponse` (incluyendo `nombre`, `rol`, `requiereCambioPassword`). Rate-limiting por `IP + usuario` (máx 5 intentos fallidos consecutivos).
  - `GET /api/auth/perfil` y `PUT /api/auth/perfil`: Consulta y edición de perfil del usuario logueado (cambio de contraseña, teléfono, avatar).
- **Alumnos (`/api/alumnos`):**
  - `GET /api/alumnos` y `GET /api/alumnos/{id}`: Padrón activo y detalle con estado contable calculado dinámicamente.
  - `POST /api/alumnos`: Alta de socio con usuario automático y una clave al azar que nadie conoce; el acceso se entrega al cobrar el primer pago o con "Generar clave temporal" (decisión 4, modo mixto).
  - `POST /api/alumnos/importar`: Importador masivo transaccional desde CSV para el Día Cero. Con `?simular=true` hace un simulacro (mismo proceso, se deshace al final) y devuelve el estado de cuenta con que quedaría cada socio, para revisarlo antes de confirmar.
  - `POST /api/alumnos/{id}/blanquear-clave`: "Generar clave temporal": crea una clave al azar, la devuelve una sola vez y marca `requiereCambioPassword=true`. Solo los socios importados en el Día Cero arrancan con el DNI como clave.
- **Caja y Finanzas (`/api/pagos`):**
  - `POST /api/pagos`: Registra cobro con guardia `HTTP 409 Conflict` si el período ya fue cubierto. Soporta estado `PARCIAL` (señas) informando `montoAbonado`.
  - `POST /api/pagos/{id}/completar`: Salda el remanente de una seña parcial y lo pasa a `PAGADO`.
  - `POST /api/pagos/{id}/anular`: Anula un comprobante exigiendo `motivo`. NUNCA se borra físicamente de la base.
  - `DELETE /api/pagos/{id}`: Redirige automáticamente a anulación administrativa para preservar la auditoría.
  - `GET /api/pagos/arqueo?fecha=YYYY-MM-DD`: Arqueo diario consolidado por método y desglose por operador.
- **Datos de cobro (`/api/configuracion/cobro`):** `GET` (cualquier usuario logueado) y `PUT` (solo ADMIN) del alias/titular de transferencia que ven los alumnos.
- **Asistencias y Tótem (`/api/asistencias`):**
  - `POST /api/asistencias/totem`: Check-in presencial en tótem autenticado por header `X-Totem-Secret`.
  - `GET /api/asistencias/totem/padron-cache`: Padrón mínimo con límites de días para funcionamiento offline.
  - `POST /api/asistencias/totem/sync`: Procesamiento de cola outbox offline con deduplicación por UUID.
  - `POST /api/asistencias/totem/latido`: Señal de vida de la tablet (una por minuto, con `X-Totem-Secret`), con los ingresos que tiene sin subir.
  - `GET /api/asistencias/totem/estado` (ADMIN): `EN_LINEA`, `SIN_SENAL` (sin señal hace más de `ATLAS_TOTEM_ALERTA_MINUTOS` en `ATLAS_TOTEM_HORARIO`), `FUERA_DE_HORARIO` o `NUNCA_CONECTADO`. La app de los titulares muestra un cartel cuando está `SIN_SENAL`.
  - `GET /api/asistencias?fecha=YYYY-MM-DD`: Consulta optimizada para polling en tiempo real.
  - `GET /api/asistencias?desde=YYYY-MM-DD&hasta=YYYY-MM-DD`: Consulta acotada para carga inicial.

---

### 5. Comandos de Verificación y Compilación

```powershell
# Ejecutar suite completa de tests unitarios y de integración (48 tests)
.\mvnw.cmd test

# Empaquetar JAR de producción omitiendo tests (genera target/backend-0.0.1-SNAPSHOT.jar)
.\mvnw.cmd -DskipTests package

# Ejecutar backend en desarrollo local
.\mvnw.cmd spring-boot:run

# Probar backup automatizado de MySQL
.\scripts\backup-mysql.ps1 -BackupDir backups

# Probar restauración de base de datos con snapshot previo automático
.\scripts\restore-mysql.ps1 -Path backups\archivo.sql.gz -Force
```

---

### 6. Variables de Entorno de Producción (`.env`)

Copiar de `.env.example`:
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/atlas_gym?serverTimezone=America/Argentina/Buenos_Aires`
- `DB_USER` y `DB_PASSWORD`
- `ATLAS_JWT_SECRET` (mínimo 64 caracteres hex / 32 bytes)
- `ATLAS_ADMIN_EMAIL`, `ATLAS_ADMIN_PASSWORD`, `ATLAS_ADMIN_NOMBRE` (Titular 1)
- `ATLAS_ADMIN2_EMAIL`, `ATLAS_ADMIN2_PASSWORD`, `ATLAS_ADMIN2_NOMBRE` (Titular 2)
- `ATLAS_TOTEM_SECRET` (secreto de la tablet del tótem)
- `ATLAS_TOTEM_HORARIO` (horario de apertura `HH:mm-HH:mm`, por defecto `06:00-23:00`) y `ATLAS_TOTEM_ALERTA_MINUTOS` (por defecto 5): cuándo avisar "Tótem sin señal"
- `ATLAS_CORS_ALLOWED_ORIGIN=http://localhost:3000,http://192.168.*.*:3000,https://tudominio.com`
- `ATLAS_DOMAIN=localhost` (o el dominio de producción)
- Túnel (producción): `COMPOSE_PROFILES=tunel`, `CLOUDFLARE_TUNNEL_TOKEN`, `ATLAS_CADDY_SITIO=:80`, `CADDY_BIND=127.0.0.1`
- `BACKUP_OFFSITE_DIR` y `RCLONE_REMOTE` (opcionales para resguardo en la nube o NAS)
