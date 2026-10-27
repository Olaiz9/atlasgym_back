# Baja administrativa de alumnos

La baja desactiva al alumno y su usuario, si existe. Conserva ficha, pagos,
asistencias y sesiones con ejercicios y series. El alumno pierde acceso,
incluso usando un JWT emitido antes de la baja.

## Contrato de la API

- DELETE /api/alumnos/{id}: solo ADMIN; baja logica, devuelve 204.
  Repetirla para una ficha existente ya inactiva devuelve 204 sin borrar datos.
- GET /api/alumnos?soloActivos=true: listado operativo sin alumnos de baja.
- GET /api/alumnos/inactivos: consulta administrativa de bajas.
- GET /api/alumnos: conserva el listado completo para compatibilidad.
- GET /api/alumnos/{id}: el administrador puede consultar la ficha inactiva.
- GET /api/pagos?mes=2026-06: el administrador sigue viendo los pagos de junio
  aunque el alumno haya sido dado de baja posteriormente.

El frontend debe usar el filtro de activos para el listado operativo, sin
eliminar los pagos historicos de Finanzas al quitar un alumno de pantalla.
No se incluye un borrado permanente ni se libera el DNI de una ficha inactiva.

## Integracion con otras ramas

Conservar AlumnoService.darDeBaja como desactivacion. No reemplazarlo por
DELETE nativos, ni desvincular pagos del alumno. @PreRemove protege frente
a eliminaciones JPA, pero no intercepta SQL nativo.

Ejecutar ./mvnw.cmd test antes de integrar. BajaAlumnoAccesoTest comprueba
el endpoint con JWT, baja repetida, historial deportivo, Finanzas, listados,
permisos y alumnos sin cuenta. AlumnoHistorialPersistenceTest comprueba
las relaciones persistidas y la proteccion frente a borrados JPA.

Estas pruebas usan H2; no sustituyen una prueba del flujo visual con MySQL.