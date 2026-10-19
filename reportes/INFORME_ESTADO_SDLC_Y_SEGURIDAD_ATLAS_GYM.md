# ATLAS GYM · AUDITORÍA TÉCNICA Y PLAN DE PRODUCCIÓN (SDLC)
**Plataforma Integral de Gestión y Entrenamiento ATLAS Gym**
*Fecha: Octubre 2026 · Versión: 1.0 (Production-Ready Assessment)*

---

## 1. Resumen Ejecutivo
- **Completitud Funcional (Features / Negocio):** 85%
- **Madurez para Producción (Seguridad y DevOps):** 65% - 70%
- **Tiempo Estimado para Lanzamiento Productivo Seguro:** 2 a 3 semanas

---

## 2. Diagnóstico del Ciclo de Vida (SDLC)
El proyecto se encuentra en la transición entre la **Fase de Integración Fullstack** y la **Fase de Pre-Lanzamiento / Endurecimiento (Staging & Hardening)**.

### Módulos Listos (Core de Negocio):
- **Gestión de Socios (95%):** CRUD, normalización DNI/celular, cálculo de estado en tiempo real.
- **Finanzas y Pagos (90%):** Emisión de cuotas, detección de morosos, historial y comprobantes WhatsApp en pestaña fija.
- **Rutinas y Videoteca (90%):** Asignación múltiple a alumnos, loops GIF biomecánicos instantáneos y catálogo por grupo muscular.
- **Tótem de Asistencias (90%):** Validación por DNI, bloqueo inteligente a cuotas vencidas y aforo en sala.
- **Login y Control de Acceso (85%):** Login unificado sin selección de rol, autodetección JWT y recuperación segura de claves.

---

## 3. Auditoría de Seguridad y Protección de Datos (OWASP)
1. **Protección contra Fuerza Bruta:** Implementar Rate Limiting con *Bucket4j* en `/api/auth/login` (máximo 5 intentos fallidos cada 2 minutos).
2. **Criptografía de Contraseñas:** Asegurar que el 100% de las contraseñas en MySQL utilicen **BCrypt (strength 10/11)** con sal criptográfica única.
3. **Manejo Seguro de JWT:** Migración gradual de `localStorage` a **Cookies HttpOnly, Secure y SameSite=Strict** para prevenir vulnerabilidades XSS.
4. **CORS Estricto:** Permitir únicamente el dominio oficial de producción bajo HTTPS.

---

## 4. Base de Datos e Infraestructura
1. **Desactivar `hibernate.ddl-auto=update`:** Fijar en `validate` e incorporar **Flyway** para migraciones SQL versionadas y auditables.
2. **Estrategia de Backups Automatizados:** Política 3-2-1 con dumps diarios comprimidos y cifrados a las 03:00 AM.
3. **Contenedores Docker:** Despliegue reproducible con `docker-compose.yml` (Next.js + Spring Boot + MySQL + Nginx con SSL Let's Encrypt).

---

## 5. Cronograma de 3 Semanas hacia Producción
- **Semana 1 (Hardening & Seguridad):** Rate limiting, BCrypt, CORS y variables de entorno aisladas.
- **Semana 2 (DevOps & Migraciones):** Flyway, Docker Compose, Nginx SSL y backups automáticos.
- **Semana 3 (Staging & Go-Live):** Despliegue en VPS/Cloud, pruebas de estrés con 50 usuarios simultáneos y salida oficial a producción.
