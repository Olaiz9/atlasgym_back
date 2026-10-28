# INFORME DE ESTADO DEL SISTEMA, ARQUITECTURA Y DIRECTIVAS PARA AGENTES (CODEX / DEV)
**Proyecto:** ATLAS GYM Management System  
**Fecha de corte:** 28 de Octubre de 2026  
**Rama activa de integración:** `ramaBruno`  
**Destinatarios:** Equipo de desarrollo (Bruno, Augusto) y Agentes de Código Autónomos (Códex / Antigravity).

---

## 1. RESUMEN EJECUTIVO Y ESTADO DE PRE-PRODUCCIÓN

El sistema se encuentra en estado **STABLE / READY FOR PRE-PRODUCTION TESTING (QA)**.  
Todos los módulos centrales han alcanzado paridad técnica y funcional entre Frontend (Next.js 16 + React 19) y Backend (Spring Boot 3.3.4 + Spring Security 6 + JPA Hibernate + MySQL 8):

- **Autenticación y Seguridad:** JWT configurado con secret aislado por entorno, bootstrap seguro del administrador inicial, y protección de endpoints sin fugas.
- **Ciclo de Alumnos y Credenciales:** Modelo validado para producción real. Los alumnos no nacen con credenciales predeterminadas ni usuarios huérfanos; sus accesos a la app se habilitan y entregan únicamente cuando abonan su membresía en Finanzas.
- **Baja y Eliminación de Alumnos en 2 Pasos + Contabilidad Inmutable:**
  1. **Paso 1 (Dar de baja):** Desde la lista de activos ("Todos"), al dar de baja a un socio se desactiva (`activo = false`) y pasa a la pestaña **"Inactivos"** por si regresa en el futuro (pudiendo reactivarse con 1 click). En Finanzas sus pagos muestran el badge ámbar **`Alumno dado de baja`**. Al recargar la página, permanece en Inactivos y no reaparece en activos.
  2. **Paso 2 (Eliminar definitivamente desde Inactivos):** Desde la pestaña **"Inactivos"**, el administrador puede eliminar definitivamente al socio (`DELETE /api/alumnos/{id}?definitivo=true`). El backend desacopla sus pagos (`pagos.alumno_id = NULL` conservando `alumno_nombre_historico` y `alumnoActivo = false`), elimina en cascada sus asistencias, rutinas, sesiones, avisos y usuario, y borra la fila de `alumnos`. Sus pagos siguen intactos en Finanzas y en el CSV exportado con la indicación **`Alumno dado de baja`**.
- **Tótem de Entrada / Kiosco:** Pantalla `/totem` protegida por PIN de terminal (sin revelar el PIN en pantalla), independiente de la sesión del navegador, inmune a hydration mismatches de React 19 y con modal de bloqueo manual desde el icono del candado.

---

## 2. MAPA DE ARCHIVOS CRÍTICOS — DIRECTIVAS "DO NOT TOUCH / PROCEDER CON CAUTELA"

Para evitar regresiones en autenticación, persistencia y navegación, **el agente Códex o cualquier desarrollador DEBE respetar estrictamente las reglas sobre los siguientes archivos:**

### A. Backend (`ATLAS_GYM/backend/backend` y `ITU TERCER-CUARTO SEMESTRE/atlasgym_back`)

#### 1. `src/main/java/com/atlas/backend/Servicio/AlumnoService.java` & `AlumnoController.java`
> [!CAUTION]
> **NO REVERTIR la lógica de `crear()`, `darDeBaja()` ni `eliminarDefinitivamente()`.**
- **En `crear()`:** `alumno.setUsuario(null)`. NO volver a crear un `Usuario` automático con contraseñas por defecto (como `dni + "123"`). La creación de credenciales se realiza de manera explícita en `asignarCredenciales()`.
- **En `darDeBaja(Long id)` (Paso 1):** Si el alumno está activo (`alumno.isActivo() == true`), lo pasa a `activo = false`, desactiva su `Usuario` y preserva su `alumnoNombreHistorico` en sus pagos. Si ya estaba inactivo (`!alumno.isActivo()`), delega en `eliminarDefinitivamente(id)`.
- **En `eliminarDefinitivamente(Long id)` (Paso 2):** Ejecuta la desvinculación contable (`p.setAlumnoNombreHistorico(alumno.getNombre()); p.setAlumno(null);`) y luego elimina en cascada `aviso_leidos`, `avisos`, `asistencias`, `registros_series`, `sesiones_ejercicios`, `sesiones_entrenamiento`, la fila en `alumnos` y finalmente el `usuario` asociado.
- **En `mapToDto()`:** Si `alumno.getUsuario() == null`, `dto.setUsuario(null)`. NO autogenerar nombres de usuario simulados, ya que el frontend se basa en este valor nulo para saber si el socio ya tiene credenciales de acceso o si aún deben crearse en Finanzas.

#### 2. `src/main/java/com/atlas/backend/Servicio/PagoService.java` & `PagoResponseDto.java`
- `PagoResponseDto` incluye `Boolean alumnoActivo` (`p.getAlumno() != null ? p.getAlumno().isActivo() : false`). Tanto si el alumno está en Inactivos (`isActivo() == false`) como si fue eliminado definitivamente (`p.getAlumno() == null`), devuelve `false` para que Finanzas muestre el badge ámbar `Alumno dado de baja`.

#### 3. `src/main/java/com/atlas/backend/Modelo/Alumno.java`
> [!WARNING]
> **NO volver a agregar un método con anotación `@PreRemove`** que lance `UnsupportedOperationException`, ya que bloquearía el Paso 2 de eliminación definitiva desde Inactivos.

#### 4. `src/main/java/com/atlas/backend/Config/AdminInitializer.java`
- Gestiona el aprovisionamiento del administrador en producción mediante variables de entorno (`ATLAS_ADMIN_BOOTSTRAP_ENABLED`, `ATLAS_ADMIN_EMAIL`, `ATLAS_ADMIN_PASSWORD`).

---

### B. Frontend (`ATLAS_GYM/frontend`)

#### 1. `app/totem/page.tsx` & `components/app-shell.tsx`
> [!IMPORTANT]
> **NO introducir `window.history.replaceState`, NO mostrar el PIN por defecto en la UI y NO ejecutar `setState` con efectos secundarios dentro de callbacks funcionales.**
- **Hydration Safe:** El componente utiliza `const [montado, setMontado] = useState(false)` para evitar que React 19 desconecte los listeners de clicks o teclado en el primer render.
- **Bloqueo manual (`bloqueadaManualmente`):** Permite que el botón del candado bloquee la terminal e inmediatamente pida el PIN de 4 dígitos sin importar si hay sesión de admin abierta.

#### 2. `app/alumnos/page.tsx` & `lib/store.tsx`
- El filtro `"TODOS"` muestra exclusivamente socios activos (`a.activo !== false && a.estadoCuenta !== "INACTIVO"`).
- El filtro `"INACTIVO"` muestra los socios dados de baja y habilita el botón **"Reactivar"** y el botón **"Eliminar definitivamente"**.

#### 3. `components/modal-nuevo-alumno.tsx` & `app/finanzas/page.tsx`
- Al inscribir un alumno en `modal-nuevo-alumno.tsx` únicamente se solicitan sus datos personales, contacto y plan.
- `app/finanzas/page.tsx` es el **único** punto donde se configuran y generan las credenciales de los alumnos al registrar un pago (`apiAsignarCredencialesAlumno`), además de mostrar el badge ámbar `Alumno dado de baja` mediante `lib/pago-historial.ts`.
