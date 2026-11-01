# DIRECTIVAS ESTRICTAS PARA AGENTE CÓDEX: AUDITORÍA DE SEGURIDAD Y PREPARACIÓN PARA PRODUCCIÓN
**Proyecto:** ATLAS GYM Management System  
**Fecha:** 28 de Octubre de 2026  
**Rama base obligatoria:** `ramaBruno` (Frontend commit `4f27e60` / Backend commit `01b7ccf`)  
**Objetivo de esta misión:** Auditar exclusivamente **baches de seguridad y alistamiento para producción (Hardening & Deployment)** SIN modificar la lógica de negocio ni los flujos funcionales ya cerrados y validados por el equipo.

---

## 1. REGLA DE ORO PARA CÓDEX (ALCANCE ESTRICTO)

> [!CAUTION]
> **PROHIBIDO MODIFICAR FLUJOS FUNCIONALES CERRADOS.**  
> Toda la lógica funcional de Alumnos, Finanzas, Login y Tótem descrita en la Sección 2 ya fue consensuada, testeada y aprobada. **No debes refactorizar, revertir ni alterar cómo funcionan estos módulos.** Tu único rol en esta etapa es revisar seguridad, blindaje de endpoints, variables de entorno y configuración de despliegue a producción.

---

## 2. FUNCIONALIDADES CERRADAS — "DO NOT TOUCH" (NO TOCAR)

### A. Baja y Eliminación de Alumnos en 2 Pasos (Backend + Frontend)
El equipo definió un proceso de **2 pasos** que ya está funcionando y persistiendo en MySQL:
1. **Paso 1 — Baja administrativa (`DELETE /api/alumnos/{id}` sin parámetros):**
   - Cuando el alumno está activo, `AlumnoService.darDeBaja(id)` lo marca con `activo = false`, desactiva su `Usuario` (`activo = false`) y asegura `alumnoNombreHistorico` en sus pagos.
   - En el frontend (`app/alumnos/page.tsx` y `lib/store.tsx`), el alumno desaparece de la pestaña **"Todos"** (que filtra estrictamente activos) y pasa a la pestaña **"Inactivos"**, desde donde puede ser **Reactivado** si regresa al gimnasio.
2. **Paso 2 — Eliminación definitiva desde Inactivos (`DELETE /api/alumnos/{id}?definitivo=true`):**
   - Cuando el alumno ya está en la pestaña **"Inactivos"** y se confirma *"Eliminar definitivamente"*, `AlumnoService.eliminarDefinitivamente(id)` desvincula sus pagos históricos (`p.setAlumno(null)` conservando `alumno_nombre_historico`), elimina sus registros dependientes (`aviso_leidos`, `avisos`, `asistencias`, `registros_series`, `sesiones_ejercicios`, `sesiones_entrenamiento`) y borra físicamente la fila en `alumnos` y en `usuarios`.
   - **NO volver a agregar `@PreRemove` en `Alumno.java`** ni bloquear el borrado físico del Paso 2.
   - **NO alterar el filtro de la pestaña "Todos" en `app/alumnos/page.tsx`**, para evitar que los dados de baja o eliminados vuelvan a aparecer al recargar la página (`F5`).

### B. Finanzas, Credenciales Diferidas y Badge de Baja
1. **Creación de usuario y contraseña exclusiva en Finanzas:**
   - En `AlumnoService.crear()` y `components/modal-nuevo-alumno.tsx`, el alumno se crea con `usuario = null` (sin credenciales automáticas).
   - Las credenciales (`usuario` y `password`) se asignan **únicamente** desde el modal de **Nuevo Pago en Finanzas** (`app/finanzas/page.tsx` -> `apiAsignarCredencialesAlumno` -> `POST /api/alumnos/{id}/credenciales`). **NO volver a autogenerar usuarios en el alta de alumnos.**
2. **Pagos históricos y etiqueta `"Alumno dado de baja"`:**
   - `PagoResponseDto.java` y `PagoService.java` envían `Boolean alumnoActivo` (`p.getAlumno() != null ? p.getAlumno().isActivo() : false`).
   - Tanto en el Paso 1 (inactivo) como en el Paso 2 (eliminado definitivo con `alumno_id = NULL`), `alumnoActivo` es `false` y el frontend (`lib/pago-historial.ts` + `app/finanzas/page.tsx`) muestra el badge ámbar **`Alumno dado de baja`** y lo incluye en la exportación CSV.

### C. Tótem de Asistencias (`/totem`) y Navegación
- `app/totem/page.tsx` está protegido por PIN de autorización de 4 dígitos (sin mostrar el PIN por defecto en pantalla), con control de montaje (`montado`) para evitar errores de hidratación en React 19, autoenfoque nativo en visor de DNI/PIN y modal de bloqueo manual en el botón del candado.
- `components/app-shell.tsx` mantiene el bypass limpio para `/totem`. **NO agregar `window.history.replaceState` ni redirecciones cruzadas en `AppShell`.**

---

## 3. QUÉ SÍ DEBE HACER CÓDEX (CHECKLIST DE SEGURIDAD Y PRODUCCIÓN)

Tu trabajo debe centrarse **exclusivamente** en auditar y fortalecer los siguientes puntos técnicos para salir a producción:

### 1. Seguridad de API y Autenticación (Spring Boot)
- [ ] **Autorización por Rol y Titularidad (IDOR):** Verificar que ningún usuario con rol `ALUMNO` pueda consultar, editar o borrar datos de otro `alumnoId` en `/api/alumnos/**`, `/api/pagos/**`, `/api/rutinas/**`, `/api/sesiones/**`, `/api/asistencias/**` ni `/api/avisos/**`.
- [ ] **Endpoint público del Tótem (`/api/asistencias/totem`):** Verificar si conviene protegerlo con un header opcional o Rate Limiting por IP para evitar ataques de fuerza bruta o enumeración masiva de DNIs en producción, **sin romper el contrato actual que consume `apiCheckInTotem(dni)` en el frontend**.
- [ ] **Rate Limiting en `/api/auth/login`:** Evaluar protección contra fuerza bruta de contraseñas en el endpoint de login.
- [ ] **Validación de Entradas y Manejo de Errores (`GlobalExceptionHandler`):** Asegurar que las respuestas de error (`500`, `400`, `403`) no filtren stacktraces internos, nombres de tablas SQL ni rutas del servidor en producción (`server.error.include-stacktrace=never`).

### 2. Configuración de Producción y Variables de Entorno
- [ ] **`application.properties` / Perfiles de Producción:**
  - Verificar que en producción se desactive el log de SQL (`spring.jpa.show-sql=${SHOW_SQL:false}`).
  - Verificar que `spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}` o `validate` sea configurable por variable de entorno.
  - Confirmar que `ATLAS_SEED_ENABLED=false` sea el valor recomendado en producción para no inyectar datos mock.
  - Confirmar que `ATLAS_JWT_SECRET`, `FRONTEND_URL`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `ATLAS_ADMIN_BOOTSTRAP_ENABLED`, `ATLAS_ADMIN_EMAIL` y `ATLAS_ADMIN_PASSWORD` estén claramente documentados en un archivo `.env.example`.

### 3. Seguridad en Frontend (Next.js)
- [ ] **Cabeceras de Seguridad HTTP (`next.config.mjs`):** Agregar o verificar cabeceras de producción seguras (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`).
- [ ] **Variables de Entorno en Frontend:** Asegurar que `NEXT_PUBLIC_TOTEM_PIN` y `NEXT_PUBLIC_API_URL` tengan su plantilla `.env.example` lista para el despliegue (sin exponer secretos sensibles de servidor con prefijo `NEXT_PUBLIC_`).
- [ ] **Modo Offline / Fallback de Demo en `lib/store.tsx`:** Verificar que cuando la aplicación corra en producción contra el backend real, no permita iniciar sesión como Admin con credenciales fallback locales si el backend rechazó la petición con `401/403` (esto ya está controlado cuando `!esFalloRed`, auditar que ningún camino permita bypass).

### 4. Docker y Despliegue
- [ ] Revisar los `Dockerfile` de Frontend y Backend y verificar si hace falta un `docker-compose.prod.yml` limpio que levante MySQL + Backend + Frontend con sus variables de entorno parametrizadas.

---

## 4. RESUMEN PARA EL AGENTE CÓDEX
1. **Hacé `git checkout ramaBruno && git pull origin ramaBruno`** en ambos repositorios antes de empezar.
2. **NO modifiques** la lógica de negocio de `AlumnoService.darDeBaja`, `AlumnoService.eliminarDefinitivamente`, `AlumnoService.crear`, `app/alumnos/page.tsx`, `app/finanzas/page.tsx` ni `app/totem/page.tsx`, salvo que sea un ajuste puramente de seguridad que no altere su comportamiento visual ni funcional.
3. Entregá un reporte de los baches de seguridad encontrados y los parches de endurecimiento (hardening) aplicados para producción.
