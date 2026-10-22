# MANUAL PASO A PASO: SUBIDA A PRODUCCIÓN (HOSTINGER VPS + CLOUDFLARE)
**Plataforma Integral de Gestión y Entrenamiento ATLAS Gym**

---

## 1. Elección del Plan en Hostinger (¿Cuál comprar?)
- **OPCIÓN CORRECTA:** **Hosting VPS** (Ícono del rayito violeta con botón *"Configurar VPS"*).
- **Por qué NO las otras:**
  - *Hosting web:* Solo corre PHP/WordPress básico, no tiene acceso root ni corre Java/Docker.
  - *Cloud hosting:* Es el mismo hosting compartido para WordPress grandes, no da control de Linux.
  - *Hosting VPS:* Es una máquina Linux (Ubuntu) real donde corre Docker con Spring Boot, Next.js y MySQL.
- **Plan Recomendado:** **KVM 2** (2 vCPU · 8 GB RAM · 100 GB NVMe) para que Spring Boot y MySQL operen holgados.

---

## 2. Hoja de Ruta Paso a Paso
1. **Hostinger:** Contratar VPS KVM 2 eligiendo la plantilla *"Docker en Ubuntu 22.04 / 24.04"*.
2. **Hostinger (Dominio):** Adquirir el dominio (ej. `atlasgym.com`).
3. **Cloudflare:** Añadir el dominio con plan Free ($0), copiar los dos Nameservers y pegarlos en Hostinger.
4. **Servidor VPS (SSH):** Conectarse por `ssh root@IP`, clonar repositorios y ejecutar `docker compose up -d --build`.
5. **Cloudflare Tunnel (Zero-Trust):** Crear túnel en Cloudflare, ejecutar comando en el VPS y mapear:
   - `atlasgym.com` ➔ `localhost:3000` (Next.js)
   - `api.atlasgym.com` ➔ `localhost:8080` (Spring Boot)
