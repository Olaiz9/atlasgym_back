# Informe Forense (Ronda 2) y Plan de Acción Secuencial — ATLAS Gym

> [!IMPORTANT]
> **Objetivo de este documento:** Documentar con evidencia exacta de código las **7 vulnerabilidades operativas, contables, de red y de escalabilidad** detectadas en la segunda auditoría escéptica (*post-remediación inicial*), y certificar la **hoja de ruta de ejecución estrictamente secuencial ("de a uno")** implementada y verificada al 100%.

---

## Parte I — Informe Técnico de Hallazgos (Auditoría Ronda 2)

### Hallazgo 1 (`CRÍTICO · DÍA 1`) — Descalce de `/api` en Docker, Trampa de `localhost` en Celulares y Backdoor Offline
- **Archivos afectados:**
  - `lib/api-client.ts`
  - `docker-compose.yml` y `.env.example`
  - `lib/store.tsx`
  - `SecurityConfig.java`
- **Diagnóstico técnico y resolución:**
  1. Se implementó un reverse proxy dinámico en runtime en `app/backend-api/[...path]/route.ts`.
  2. Todas las llamadas del navegador van al mismo origen (`/backend-api/*`), evitando la trampa de `localhost:8080` en los teléfonos celulares de los dueños conectados por Wi-Fi.
  3. Se eliminó el login fantasma con fallback `admin123` en producción (`SPRING_PROFILES_ACTIVE=prod`).

---

### Hallazgo 2 (`CRÍTICO · ESCALA`) — Polling cada 5 s en `/asistencias` sin Filtro de Fecha (`O(Historia Total)`)
- **Archivos afectados:**
  - `lib/store.tsx` y `app/asistencias/page.tsx`
  - `AsistenciaController.java` y `AsistenciaService.java`
- **Diagnóstico técnico y resolución:**
  1. El polling periódico a `/api/asistencias` ahora envía obligatoriamente `?fecha=YYYY-MM-DD` (hoy).
  2. La carga inicial se acota con `?desde=&hasta=`.
  3. La caché local en `localStorage` (`atlas_asistencias_v2`) se limitó a 7 días / 500 registros para prevenir desbordes de cuota (`QuotaExceededError`).

---

### Hallazgo 3 (`ALTO · OPERACIÓN`) — Ilusión del Modo Offline en el Tótem y Exposición de `NEXT_PUBLIC_TOTEM_SECRET`
- **Archivos afectados:**
  - `app/totem/page.tsx`, `lib/totem-offline.ts`
  - `AsistenciaController.java`, `V2__totem_outbox_e_indices.sql`
- **Diagnóstico técnico y resolución:**
  1. Se removió `NEXT_PUBLIC_TOTEM_SECRET` del bundle cliente. La vinculación de la tablet física se guarda localmente en `localStorage` (`atlas_totem_device_secret`).
  2. Se creó el endpoint protegido `GET /api/asistencias/totem/padron-cache` para precargar el padrón mínimo con límites de días.
  3. Se creó la cola Outbox (`atlas_totem_outbox_v1`) y el endpoint `POST /api/asistencias/totem/sync` con UUIDs (`clientEventId`) para sincronización offline idempotente sin duplicación de ingresos.

---

### Hallazgo 4 (`ALTO · SEGURIDAD`) — DoS Trivial sobre Login de Dueños, Clave Inicial = DNI y Cuenta `ADMIN` Compartida
- **Archivos afectados:**
  - `AuthService.java`, `DataInitializer.java`, `V3__hardening_identidad.sql`
- **Diagnóstico técnico y resolución:**
  1. Rate-limiting por tupla `IP + usuario` (tope de 5 intentos fallidos consecutivos por usuario) evitando que ataques contra un titular bloqueen al otro hermano o a la red del gimnasio.
  2. Soporte nativo para 2 cuentas nominales de administradores independientes (`ATLAS_ADMIN_*` y `ATLAS_ADMIN2_*`).
  3. Forzado de cambio de clave en el primer ingreso de alumnos (`requiere_cambio_password`) y blanqueo a DNI con 1 clic desde `/alumnos/[id]`.

---

### Hallazgo 5 (`ALTO · CAJA REAL`) — Doble Cobro, Falta de Señas/Pagos Parciales, Imputación de `MOROSO`, `DELETE` Físico y Sin Arqueo Diario
- **Archivos afectados:**
  - `PagoService.java`, `PagoController.java`, `V4__caja_real_argentina.sql`, `app/finanzas/page.tsx`
- **Diagnóstico técnico y resolución:**
  1. Guardia de concurrencia `HTTP 409 Conflict` si se intenta cobrar un período que ya se encuentra cubierto.
  2. Soporte de pagos parciales / señas (`EstadoPago.PARCIAL`, `monto_abonado`) y endpoint `POST /api/pagos/{id}/completar`.
  3. Anulación contable auditada (`EstadoPago.ANULADO`) con motivo obligatorio y registro del titular operante en reemplazo del borrado físico `DELETE`.
  4. Panel de **Arqueo de Caja del Día** desglosado por método (Efectivo, Transferencia, Tarjeta) y por titular a cargo.

---

### Hallazgo 6 (`MEDIO-ALTO · DÍA CERO`) — Ausencia de Importador Masivo CSV y Acciones Rápidas por WhatsApp
- **Archivos afectados:**
  - `AlumnoController.java`, `AlumnoService.java`, `lib/csv-import.ts`, `lib/whatsapp-utils.ts`, `app/alumnos/page.tsx`
- **Diagnóstico técnico y resolución:**
  1. Importador transaccional masivo `POST /api/alumnos/importar` con previsualización, matching de planes tolerante a mayúsculas/acentos y seeding opcional del último mes pago.
  2. Botones directos de WhatsApp 1-clic (`wa.me`) con normalización de celulares argentinos (`+54 9`, remoción de prefijos `0` y `15`) para credenciales y recordatorios de cuota.

---

### Hallazgo 7 (`MEDIO-ALTO · INFRA`) — Backups en el Mismo Disco Físico y Tráfico HTTP sin TLS
- **Archivos afectados:**
  - `docker-compose.yml`, `Caddyfile`, `scripts/backup-mysql.*`, `scripts/restore-mysql.*`
- **Diagnóstico técnico y resolución:**
  1. Configuración de reverse proxy HTTPS automático con Caddy 2 (`Caddyfile` y servicio en `docker-compose.yml`).
  2. Scripts de backup actualizados con prueba de descompresión gzip y hooks offsite hacia carpeta sincronizada (`BACKUP_OFFSITE_DIR`) y nube (`RCLONE_REMOTE`).
  3. Scripts de restauración interactiva con guardia antidesastres y snapshot de seguridad previo automático (`pre_restore_*.sql.gz`).

---

## Parte II — Certificación de Verificación Integral

- **Backend:** 44/44 tests JUnit en verde (`BUILD SUCCESS`). Migraciones Flyway V1 a V4 validadas con Hibernate `ddl-auto=validate`.
- **Frontend:** 151/151 tests Vitest en verde (19 suites).
- **TypeScript:** 0 errores (`tsc --noEmit`).
- **Next.js Production Build:** Compilación limpia de 13 rutas.
- **MySQL 8.0 Real:** Smoke test integral E2E con 30 aserciones de negocio verificadas.
