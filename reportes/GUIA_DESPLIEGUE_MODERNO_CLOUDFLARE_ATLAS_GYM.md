# GUÍA DE DESPLIEGUE MODERNO: CLOUDFLARE, HOSTINGER VPS Y DOCKER
**Plataforma Integral de Gestión y Entrenamiento ATLAS Gym**
*Arquitectura Zero-Trust, Cero Puertos Abiertos y Contenedores Multi-Stage*

---

## 1. La Topología Moderna (El Trío de Infraestructura)
1. **Hostinger (VPS Ubuntu):** El músculo de cómputo donde corren los contenedores Docker aislados.
2. **GitHub:** El repositorio central donde reside el código fuente y desde donde el VPS descarga actualizaciones (`git pull`).
3. **Cloudflare (Zero-Trust):** El escudo perimetral que administra el dominio, provee HTTPS automático y canaliza el tráfico mediante un túnel cifrado saliente (**Cloudflare Tunnel**).

---

## 2. Titularidad y Acceso Técnico
- **Cuentas a nombre del cliente:** El dominio y el hosting son contratados por el dueño del gimnasio con sus datos de facturación para proteger la propiedad legal del activo.
- **Delegación técnica:**
  - En **Hostinger**: El cliente usa *Account Sharing* e invita al programador.
  - En **Cloudflare**: El cliente usa *Members / IAM* y asigna rol de *Administrator*.
  - El desarrollador opera con su propio usuario sin ver números de tarjeta ni facturación.

---

## 3. La Solución Moderna: Cloudflare Tunnel (cloudflared)
- **Cero puertos de entrada abiertos:** Se cierran los puertos 80, 443 y 3306 en el cortafuegos de Hostinger.
- **IP oculta:** La dirección IP del servidor nunca se publica en internet.
- **SSL automático:** Certificado HTTPS gestionado por Cloudflare en sus servidores de borde.
- **Ruteo interno:**
  - `atlasgym.com` ➔ `http://localhost:3000` (Next.js)
  - `api.atlasgym.com` ➔ `http://localhost:8080` (Spring Boot)

---

## 4. Contenedores Docker
- **Backend:** Spring Boot empaquetado en imagen JRE 17 Alpine no-root (~180 MB).
- **Frontend:** Next.js optimizado con `output: 'standalone'` sobre Node 20 Alpine (~100 MB).
- **MySQL 8:** Persistencia mediante volumen de disco (`mysql_data`) y red privada interna sin acceso desde internet.

---

## 5. Ciclo de Actualizaciones
1. En tu máquina: `git push origin main`
2. En el VPS: `git pull && docker compose up -d --build` (actualización sin cortes en menos de 45 segundos).
