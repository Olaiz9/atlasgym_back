# INFORME DE ESTADO DEL SISTEMA, ARQUITECTURA Y DIRECTIVAS PARA AGENTES (CODEX / DEV)
**Proyecto:** ATLAS GYM Management System  
**Fecha de corte:** 25 de Octubre de 2026  
**Rama activa de integración:** `ramaBruno`  
**Destinatarios:** Equipo de desarrollo (Bruno, Augusto) y Agentes de Código Autónomos (Códex / Antigravity).

---

## 1. RESUMEN EJECUTIVO Y ESTADO DE PRE-PRODUCCIÓN

El sistema se encuentra en estado **STABLE / READY FOR PRE-PRODUCTION TESTING (QA)**.  
Todos los módulos centrales han alcanzado paridad técnica y funcional entre Frontend (Next.js 16 + React 19) y Backend (Spring Boot 3.3.4 + Spring Security 6 + JPA Hibernate + MySQL 8):

- **Autenticación y Seguridad:** JWT configurado con secret aislado por entorno, bootstrap seguro del administrador inicial (`admin@atlasgym.com`), y protección de endpoints sin fugas.
- **Ciclo de Alumnos y Credenciales:** Modelo híbrido validado para producción real. Los alumnos no nacen con credenciales predeterminadas ni usuarios huérfanos; sus accesos a la app se habilitan y entregan únicamente cuando abonan su membresía en Finanzas.
- **Contabilidad Inmutable vs Bajas:** Eliminación limpia de alumnos en cascada (asistencias, rutinas, avisos, usuarios) sin violar restricciones de clave foránea y desacoplando pagos históricos (`pagos.alumno_id = NULL` conservando `alumno_nombre_historico`).
- **Tótem de Entrada / Kiosco:** Pantalla `/totem` protegida por PIN de terminal (`1234`), independiente de la sesión del navegador, inmune a hydration mismatches de React 19 y con modal de bloqueo manual.

---

## 2. MAPA DE ARCHIVOS CRÍTICOS — DIRECTIVAS "DO NOT TOUCH / PROCEDER CON CAUTELA"

Para evitar regresiones en autenticación, persistencia y navegación, **el agente Códex o cualquier desarrollador DEBE respetar estrictamente las reglas sobre los siguientes archivos:**

### A. Backend (`ATLAS_GYM/backend/backend` y `ITU TERCER-CUARTO SEMESTRE/atlasgym_back`)

#### 1. `src/main/java/com/atlasgym/backend/service/AlumnoService.java`
> [!CAUTION]
> **NO REVERTIR la lógica de `crear()` ni `darDeBaja()`.**
- **En `crear()`:** `alumno.setUsuario(null)`. NO volver a crear un `Usuario` automático con contraseñas por defecto (como `dni + "123"`). La creación de credenciales se realiza de manera explícita en `asignarCredenciales()`.
- **En `darDeBaja(Long id)`:** Ejecuta la desvinculación contable mediante query nativa:
  ```sql
  UPDATE pagos SET alumno_id = NULL, alumno_nombre_historico = :nombre WHERE alumno_id = :id;
  ```
  Luego elimina en cascada `aviso_leidos`, `avisos`, `asistencias`, `registros_series`, `sesiones_ejercicios`, `sesiones_entrenamiento`, la fila en `alumnos` y finalmente el `usuario` asociado. Si se reintroduce una cascada directa JPA sin desvincular `pagos`, la base de datos lanzará `MySQLIntegrityConstraintViolationException (Error 1451)`.
- **En `mapToDto()`:** Si `alumno.getUsuario() == null`, `dto.setUsuario(null)`. NO autogenerar nombres de usuario simulados, ya que el frontend se basa en este valor nulo para saber si el socio ya tiene credenciales de acceso o si aún deben crearse en Finanzas.

#### 2. `src/main/java/com/atlasgym/backend/model/Alumno.java`
> [!WARNING]
> **NO volver a agregar un método con anotación `@PreRemove`** que haga `this.usuario = null` o manipule relaciones en memoria durante la eliminación de JPA. Eso genera un conflicto de ciclo de vida con Hibernate impidiendo las bajas físicas.

#### 3. `src/main/java/com/atlasgym/backend/config/SecurityConfig.java` & `JwtAuthenticationFilter.java`
- Permite acceso público sin token a:
  - `/api/auth/**` (login).
  - `/api/asistencias/totem` (check-in de DNI desde terminal de recepción).
- Todos los demás endpoints requieren `Bearer JWT`. Si se modifica la cadena de filtros (`SecurityFilterChain`), se corre el riesgo de bloquear el Tótem o exponer datos de finanzas.

#### 4. `src/main/java/com/atlasgym/backend/config/AdminUserInitializer.java`
- Garantiza que al arrancar el backend exista el usuario administrador `admin@atlasgym.com` / `admin123` con rol `ADMIN`. No modificar sus credenciales en código duro; en producción utilizar variables de entorno.

---

### B. Frontend (`ATLAS_GYM/frontend`)

#### 1. `app/totem/page.tsx`
> [!IMPORTANT]
> **NO introducir `window.history.replaceState` ni ejecutar `setState` con efectos secundarios dentro de callbacks funcionales.**
- **Hydration Safe:** El componente utiliza `const [montado, setMontado] = useState(false)`. Mientras `montado` es falso, renderiza el loader inicial estático. Esto previene que React 19 rompa los listeners de clicks o teclado por discrepancia de SSR vs cliente.
- **Teclado dual:** Mantiene referencias sincrónicas (`pinInputRef`, `inputPinRef`, `inputDniRef`) con autoenfoque e interceptores que previenen la acción por defecto (`e.preventDefault()`). Si se retira `e.preventDefault()`, los navegadores pierden foco con la tecla Backspace o disparan atajos.
- **Bloqueo manual (`bloqueadaManualmente`):** Permite que un administrador que esté operando en la misma máquina pueda bloquear el tótem y probar el PIN sin que la sesión de su cookie/token lo auto-autorice.

#### 2. `components/app-shell.tsx`
> [!WARNING]
> **NO agregar redirecciones forzadas de `/totem` en el `useEffect` global.**
- La ruta `/totem` tiene un bypass especial (`if (pathname === '/totem') return;`) porque la terminal maneja su propia seguridad interna mediante PIN de hardware y modo kiosco. Redirigir alumnos desde `AppShell` a `/` genera carreras de navegación y desincroniza el router de Next.js.

#### 3. `components/modal-nuevo-alumno.tsx`
- Se removió el box visual de "Usuario y contraseña autogenerados". Al inscribir un alumno únicamente se solicitan sus datos personales, contacto y plan. No volver a colocar campos de credenciales en este modal.

#### 4. `app/finanzas/page.tsx`
- Es el **único** punto donde se configuran y generan las credenciales de los alumnos mediante el modal de cobro (`handleNuevoPago`). Si se ingresan usuario y contraseña, se dispara `apiAsignarCredencialesAlumno(...)` sincronizando en tiempo real con el backend.

#### 5. `lib/store.tsx`
- Funciona como "El Amortiguador" (Single Source of Truth).
- Sincroniza optimisticamente con `localStorage` y envía llamadas asíncronas vía API client (`apiActualizarAlumno`, `apiDarDeBajaAlumno`, etc.).
- La función `eliminarAlumno(id)` no solo marca inactivo, sino que remueve al alumno de la lista activa en memoria y llama al endpoint de baja física.

---

## 3. HISTORIAL DE COMMITS Y SINCRONIZACIÓN DE HOY

### Frontend (`https://github.com/Olaiz9/atlasgym_front.git` - rama `ramaBruno`)
- `3579323`: `fix(totem): resolver respuesta inicial sin recarga, autoenfoque y modal candado para desvincular`
- `fe31b62`: `fix(totem): corregir captura de teclado fisico, teclado en pantalla y boton volver en pantalla pin`
- `bf4a45c`: `feat(totem): proteccion de terminal con PIN maestro y bloqueo para alumnos logueados`
- `9328e11`: `fix(alumnos): eliminar alumnos definitivamente de la lista y padron preservando pagos en finanzas`
- `b60a140`: `feat(alumnos,finanzas): enfoque hibrido de baja/eliminacion y generacion de credenciales exclusiva en finanzas`
- `4b4a1f1`: `fix(finanzas,alumnos): sincronizar credenciales asignadas al registrar pago y clarificar clave inicial por DNI`

### Backend (`https://github.com/Olaiz9/atlasgym_back.git` - rama `ramaBruno`)
- `c571b45`: `fix(alumnos): eliminacion fisica completa de alumno y usuario desvinculando pagos historicos`
- `ba9fbbe`: `feat(alumnos): eliminar fisico cuando no hay historial y diferir creacion de credenciales al pago en finanzas`
- `a076d10`: `merge: incorporar mejoras de seguridad JWT y bootstrap de administrador de ramaAugusto`
- `f96cebc`: `fix(auth): hacer explicita la creacion del administrador inicial`
- `661712c`: `fix(jwt): retirar secreto publicado y configurar clave por entorno`

---

## 4. GUÍA DE TESTING DE PRE-PRODUCCIÓN (QA CHECKLIST)

Antes del despliegue final a producción en servidor / VPS / Vercel, ejecutar las siguientes pruebas de verificación:

| # | Escenario de Prueba | Pasos de Verificación | Resultado Esperado |
|---|---|---|---|
| **1** | **Bootstrap & Login Admin** | Entrar a `/login` con `admin@atlasgym.com` / `admin123`. | Acceso inmediato al dashboard de administración con token JWT válido. |
| **2** | **Inscripción de Alumno Limpia** | Ir a `/alumnos`, click en "Nuevo Alumno", cargar datos y guardar. | Se crea en MySQL sin usuario asignado (`usuario = null`). No hay credenciales huérfanas. |
| **3** | **Cobro y Asignación de Credenciales** | Ir a `/finanzas`, click en "Nuevo Pago", seleccionar el alumno, cargar usuario y contraseña (ej: `prueba` / `123456`). | El pago se asienta y en el backend se crea el `Usuario` con rol `ALUMNO` y password encriptado con BCrypt. |
| **4** | **Login del Alumno** | Cerrar sesión e iniciar con las credenciales creadas (`prueba` / `123456`). | Ingreso al portal de alumno con sus rutinas, estado de cuota y sin acceso a módulos de administración. |
| **5** | **Check-in en Tótem** | Ir a `/totem` (o tocar botón Tótem en `/asistencias`). Ingresar PIN `1234` la primera vez. Ingresar el DNI del alumno. | Registra asistencia en tiempo real, reproduce sonido de éxito y muestra auto-reset de 3.5 segundos. |
| **6** | **Seguridad del Tótem (Candado)** | En `/totem`, hacer click en el candado superior derecho y seleccionar "Bloquear terminal ahora". | La terminal se bloquea inmediatamente y requiere el PIN `1234` para reactivarse. |
| **7** | **Eliminación y Preservación Contable** | Como administrador en `/alumnos`, eliminar al alumno de prueba. | El alumno desaparece de `/alumnos`. En `/finanzas` su pago histórico se mantiene intacto con su nombre registrado. La base de datos no arroja error de FK. |

---

## 5. RECOMENDACIONES PARA EL AGENTE CÓDEX

Si el agente Códex va a realizar tareas adicionales (nuevas funcionalidades, reportería, mejoras estéticas):
1. **Mantenerse sobre la rama `ramaBruno`** o crear una rama basada en el commit `3579323` (front) y `c571b45` (back).
2. **No alterar las claves de localStorage** prefijadas con `atlas_` (`atlas_totem_terminal_autorizada`, `atlas_sesion_v1`, `atlas_jwt_token_v1`), ya que el Store y el API Client dependen de ellas para el Amortiguador optimista.
3. **Preservar el mapeo de IDs numéricos vs string:** El backend utiliza IDs `Long` (numéricos) y el frontend utiliza `string` en TypeScript (`parseNumericId()` en `api-mappers.ts`). No alterar los mappers existentes.
