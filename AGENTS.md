# AGENTS.md — Reglas para TODAS las IAs del proyecto (backend)

Este archivo lo leen Codex (Augusto), Antigravity (Bruno, vía GEMINI.md) y Claude (Lucas).
Es la **única fuente de verdad** sobre cómo se trabaja en este repo. El contexto de negocio
(cuotas, caja, tótem) está en `CLAUDE.md`, secciones 1, 3 y 4: leelo antes de tocar lógica.

## Cómo se organiza el trabajo

- Claude (con Lucas) coordina: define prioridades y escribe cada tarea como un **Issue de GitHub**
  con un brief (contexto, archivos, qué hacer, tests requeridos, qué NO tocar).
- **Hacé solo lo que pide el Issue.** Nada de refactors, renombres o "mejoras" fuera de alcance.
  Si encontrás otro problema, anotalo en el PR (sección "Encontré además") en vez de arreglarlo.
- Si el brief es ambiguo o contradice el código, **preguntá en el Issue antes de inventar**.

## Flujo de Git (obligatorio)

1. Partí siempre de `develop` actualizado: `git checkout develop && git pull`.
2. Una rama por Issue: `feat/<nro>-descripcion-corta` o `fix/<nro>-descripcion-corta`.
3. Commits en español: `tipo(area): descripcion` (tipos: feat, fix, test, refactor, docs, ci, chore).
4. **Nunca** subas directo a `develop` ni a `main`, y nunca uses `--force` sobre ramas compartidas.
5. Abrí un Pull Request hacia `develop` completando la plantilla. Se mergea solo con el CI en verde
   y la aprobación de otra persona.

## Antes de abrir el PR

```powershell
.\mvnw.cmd clean test      # todos los tests deben pasar (usan H2, no necesitan MySQL)
```

El CI de GitHub además construye la imagen Docker y corre el **ensayo de deploy** (docker compose
completo con MySQL 8 + prueba de humo + simulacro de backup/restore). Si el CI falla, el PR no entra.

## Reglas duras (no negociables)

- **Esquema de base:** nunca modifiques una entidad JPA sin una migración Flyway nueva
  (`src/main/resources/db/migration/V<siguiente>__descripcion.sql`). **Nunca edites una migración
  existente.** En producción `ddl-auto=validate`: si entidad y tabla no coinciden, el sistema no arranca.
- **No inventes columnas ni tablas.** Antes de escribir SQL o JPQL, verificá el nombre real en las
  migraciones. (Un `DELETE` con una columna inexistente ya rompió el borrado de socios una vez.)
- **Todo cambio lleva test.** Un bug: primero un test que falle, después el arreglo.
  Una función nueva: tests de su caso normal, sus errores (400/404/409) y sus permisos (401/403).
- **Permisos:** todo endpoint que modifica datos o muestra datos de otros socios lleva
  `@PreAuthorize("hasRole('ADMIN')")` o una verificación explícita del dueño del dato.
- **Pagos:** nunca se borran físicamente; se anulan con motivo y quedan auditados (`anuladoPor`).
- **Claves:** nunca devuelvas contraseñas ni hashes en una respuesta, nunca las loguees, nunca
  pongas claves o secretos por defecto en `application*.properties`. La clave inicial del socio
  sigue el "modo mixto": aleatoria en el alta y al blanquear; DNI solo en la importación masiva.
- **Regla de cobro (Opción A):** `AlumnoService.calcularEstadoCuenta` está cubierta por
  `CuotasEstadoYMultiMesTest`. No la cambies sin una decisión explícita en el Issue.
- **`DECISION PENDIENTE`:** esos comentarios marcan decisiones del equipo. No las resuelvas por tu cuenta.
- **Lo propio de cada gimnasio va a configuración** (tabla, propiedad o variable de entorno), nunca
  fijo en el código: el sistema se va a vender a otros gimnasios.
- **No commitees** `.env`, `backups/`, logs, `target/` ni archivos personales.

## Estructura

```
src/main/java/com/atlas/backend/
  Controlador/   endpoints REST (/api/...)       Servicio/     lógica de negocio
  Repositorio/   Spring Data JPA                 Modelo/       entidades JPA
  Dto/           records de entrada/salida       Seguridad/    JWT, SecurityConfig
  Config/        inicializadores                 Excepciones/  BusinessException(mensaje, status)
src/main/resources/db/migration/   migraciones Flyway (V1..Vn)
src/test/java/com/atlas/backend/   tests (@SpringBootTest + MockMvc, ver PlanesTest como modelo)
scripts/                           smoke test, backup/restore
```

## Estilo

- Código, comentarios, mensajes de error y commits en **español** (mensajes al usuario en voseo).
- Errores de negocio: `throw new BusinessException("mensaje claro para el titular", 409)`.
- Seguí el estilo del archivo que estás tocando; no reformatees código ajeno.
