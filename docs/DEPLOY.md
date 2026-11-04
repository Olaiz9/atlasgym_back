# Guía de deploy — ATLAS Gym

Paso a paso para instalar ATLAS en el servidor del gimnasio, dejarlo listo para el primer día y mantenerlo andando.
Está escrita para que la siga cualquiera del equipo, aunque nunca haya hecho un deploy.

> **Regla de oro:** este repo es público. Ninguna clave, nombre de titular, teléfono, alias de cobro ni el
> archivo `.env` se sube a Git. Todo eso vive **solo en el servidor** (y en el gestor de claves del equipo).

---

## 0. Qué se instala

```
Internet ──► Caddy (HTTPS, puertos 80/443)
               ├── /api/*  ──► backend  (Spring Boot, :8080 solo interno)
               └── resto   ──► frontend (Next.js,     :3000 solo interno) ──► backend
                                                     backend ──► MySQL 8 (:3307 solo interno)
```

Son 4 contenedores de Docker definidos en `docker-compose.yml`. Desde Internet solo se ven los puertos
80 y 443; la base, el back y el front escuchan únicamente dentro del servidor (el CI lo verifica en cada PR).

---

## 1. Qué hace falta

### El servidor
| Requisito | Mínimo | Recomendado |
|---|---|---|
| Sistema | Ubuntu 22.04 / 24.04 (Linux de 64 bits) | — |
| Memoria | 2 GB | 4 GB (MySQL + Java + Node juntos) |
| Disco | 20 GB | 40 GB (los backups ocupan) |
| Red | IP pública con puertos **80 y 443** abiertos | — |
| Software | Docker Engine + plugin `docker compose`, `git`, `curl` | — |

> **Hosting todavía sin definir** (VPS pago mensual, Oracle Always Free o una PC en el local).
> Esta guía sirve igual para las tres opciones con IP pública. Si termina siendo una PC del local
> **sin** IP pública, ver la sección 9.

### Antes del día de la instalación (pedirle al gimnasio)
- [ ] **Dominio** (ej. `gym.ejemplo.com.ar`) y acceso para crear un registro DNS.
- [ ] **Mail de cada titular** (es su usuario para entrar).
- [ ] **Planes y precios** vigentes.
- [ ] **Excel de socios** (ver paso 6.3).
- [ ] **Alias / CBU de cobro y titular de la cuenta** (se cargan desde la app, nunca en el código).
- [ ] **PIN de 4 dígitos** que quieran para la tablet del tótem.

### Antes del día (en el código, por PR)
- [ ] El contacto de privacidad está completo en `lib/datos-legales.ts` del front (`contactoPrivacidad`).
      Si falta, `/privacidad` muestra "datos pendientes".
- [ ] Lo que se va a instalar está en `main` en **los dos repos** (paso 8: "Publicar una versión").

---

## 2. Preparar el servidor (una sola vez)

```bash
# Docker (script oficial) y herramientas
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER      # cerrar sesión y volver a entrar para que tome efecto
sudo apt-get install -y git curl

# Firewall: solo SSH, HTTP y HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

**DNS:** en el proveedor del dominio, crear un registro **A** que apunte el dominio a la IP pública del
servidor. Comprobar antes de seguir (tiene que devolver la IP del servidor):

```bash
getent hosts gym.ejemplo.com.ar
```

> Sin este paso Caddy no puede sacar el certificado HTTPS.

---

## 3. Descargar el código

Los dos repos van en **carpetas hermanas**, en la rama `main`:

```bash
sudo mkdir -p /opt/atlas && sudo chown $USER /opt/atlas && cd /opt/atlas
git clone --branch main https://github.com/Olaiz9/atlasgym_back.git
git clone --branch main https://github.com/Olaiz9/atlasgym_front.git
cd atlasgym_back
```

---

## 4. Completar el `.env`

```bash
cp .env.example .env
chmod 600 .env          # solo el dueño del archivo puede leerlo
nano .env
```

Generar cada clave con un comando, **nunca inventarla a mano**:

```bash
openssl rand -hex 32    # ATLAS_JWT_SECRET
openssl rand -hex 24    # ATLAS_TOTEM_SECRET, DB_PASSWORD, MYSQL_ROOT_PASSWORD (uno distinto para cada una)
```

| Variable | Qué poner |
|---|---|
| `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Claves generadas, distintas entre sí |
| `ATLAS_JWT_SECRET` | Clave generada de 64 caracteres |
| `ATLAS_ADMIN_EMAIL` / `ATLAS_ADMIN2_EMAIL` | Mail de cada titular |
| `ATLAS_ADMIN_PASSWORD` / `ATLAS_ADMIN2_PASSWORD` | Clave **temporal** de 12+ caracteres para el primer ingreso |
| `ATLAS_ADMIN_NOMBRE` / `ATLAS_ADMIN2_NOMBRE` | Nombre de cada titular (aparece en la caja y el arqueo) |
| `ATLAS_TOTEM_SECRET` | Clave generada. **Obligatoria**: sin ella cualquiera puede marcar asistencias |
| `ATLAS_TOTEM_PIN` | El PIN de 4 dígitos que eligió el gimnasio (no dejar `1234`) |
| `ATLAS_DOMAIN` | El dominio, sin `https://` (ej. `gym.ejemplo.com.ar`) |
| `ATLAS_CORS_ALLOWED_ORIGIN` | `https://` + el dominio (ej. `https://gym.ejemplo.com.ar`) |

El resto se deja como viene. Guardar una copia del `.env` en el gestor de claves del equipo: sin él,
los backups no se pueden restaurar con las mismas credenciales.

---

## 5. Levantar el sistema

```bash
./scripts/actualizar.sh
```

El script hace todo en orden y **se frena si algo está mal**:

1. Revisa el `.env`: corta si quedó algún `CAMBIAR_...`, si falta el secreto del tótem o si el PIN no tiene 4 dígitos.
2. Si la base ya existe, hace un **backup antes de tocar nada**.
3. Trae el código nuevo de los dos repos (`git pull`).
4. Construye y levanta los contenedores (la primera vez tarda 5–10 minutos).
5. Espera a que todo responda.

Al terminar, abrir `https://<dominio>/login`: abajo tiene que decir `ATLAS Gym Platform · v1.0.0 · <commit>`.

### Apagar el alta inicial de titulares (importante)
Después de que **los dos titulares** entraron una vez (paso 6.1):

```bash
nano .env
#   ATLAS_ADMIN_BOOTSTRAP_ENABLED=false
#   ATLAS_ADMIN_PASSWORD=          (borrar el valor)
#   ATLAS_ADMIN2_PASSWORD=         (borrar el valor)
docker compose up -d backend
```

Así las claves temporales no quedan escritas en el servidor. Las cuentas ya creadas siguen funcionando.

---

## 6. Checklist del primer día (con los titulares)

### 6.1 Cuentas
- [ ] Cada titular entra con su mail y la clave temporal.
- [ ] Cada uno cambia su clave desde **Mi perfil** (tocando su nombre en el menú).
- [ ] Se apaga el alta inicial (paso 5).

### 6.2 Configuración
- [ ] **Planes:** crear los planes con su precio y días por semana (**Planes**). Hacerlo **antes** de importar:
      el importador asigna cada socio al plan con el nombre que figure en la planilla.
- [ ] **Datos de cobro:** Finanzas → **Cargar alias**: alias o CBU y titular de la cuenta.
      Comprobar que aparece en la tarjeta "Datos para abonar tu cuota".

### 6.3 Importar los socios (Día Cero)
- [ ] En Excel: **Archivo → Guardar como → "CSV UTF-8 (delimitado por comas)"**. Subir el `.xlsx` directo no funciona (la pantalla avisa).
- [ ] Columnas obligatorias: **Nombre** y **DNI**. Opcionales: Celular, Email, Plan, Último mes pago, Monto, Método.
- [ ] Alumnos → **Importar**: revisar la vista previa y las observaciones **antes** de confirmar.
- [ ] Al terminar, **leer todos los avisos del servidor** (planes que no existen, DNI repetidos, mails inválidos,
      cuotas sin monto). Corregir a mano lo que haga falta.
- [ ] Revisar 3 o 4 socios al azar: plan, estado de la cuota y que la cuota importada aparezca en Finanzas.

### 6.4 Tablet del tótem
- [ ] En la tablet, abrir `https://<dominio>/totem`.
- [ ] Ingresar el **PIN** (`ATLAS_TOTEM_PIN`).
- [ ] Cuando pida la **clave de la terminal**, escribir el valor de `ATLAS_TOTEM_SECRET` (una sola vez).
- [ ] Anclar la página a la pantalla de inicio (botón "¿Cómo anclar ATLAS…?" del login).
- [ ] Probar un ingreso con el DNI de un socio real y verlo en **Asistencias**.
- [ ] Probar sin Internet: apagar el Wi-Fi, marcar un ingreso, prender el Wi-Fi y comprobar que se sincroniza.

### 6.5 Control final
- [ ] Cobrar una cuota de prueba y **anularla** (con motivo). Revisar que el arqueo del día la excluya.
- [ ] `https://<dominio>/privacidad` carga sin el cartel de "datos pendientes".
- [ ] A un socio de prueba: ficha → **Generar clave temporal**; entra con su DNI y esa clave, y la app le pide elegir una propia.
- [ ] Backups automáticos programados (paso 7) y **un backup manual** hecho al terminar el día.

---

## 7. Backups

El sistema guarda plata y datos de personas: **sin backups no se entrega**.

```bash
crontab -e
# Todos los días a las 03:30
30 3 * * * /opt/atlas/atlasgym_back/scripts/backup-mysql.sh >> /opt/atlas/backup.log 2>&1
```

- Se guardan en `backups/`, comprimidos, y se borran solos los de más de 14 días.
- **Copia fuera del servidor (obligatoria):** si se rompe el disco, se pierden también los backups locales.
  Configurar `RCLONE_REMOTE` (ej. una carpeta de Google Drive) o `BACKUP_OFFSITE_DIR` en el `.env`.
- **Restaurar** (hace un backup de seguridad antes de pisar nada):
  ```bash
  ./scripts/restore-mysql.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
  ```
- Una vez por mes, comprobar que el último backup existe y pesa algo razonable. El CI ya ensaya
  backup → borrar la base → restaurar en cada PR, pero conviene mirar el del servidor real.

---

## 8. Publicar una versión nueva

El servidor siempre corre **`main`**. El trabajo del día a día va a `develop`.

1. **Release:** cuando `develop` está estable, abrir un PR `develop → main` en **cada repo** que cambió,
   con CI en verde y aprobación, igual que cualquier PR.
2. Si cambió la versión, subirla en `package.json` del front (ej. `1.0.0` → `1.1.0`).
3. **En el servidor**, fuera del horario pico:
   ```bash
   cd /opt/atlas/atlasgym_back
   ./scripts/actualizar.sh
   ```
4. Comprobar que el commit del login es el nuevo y hacer un ingreso de prueba.

**Si la versión nueva sale mal:** volver al commit anterior en el repo que falló y relanzar.
```bash
git -C ../atlasgym_front log --oneline -5      # buscar el commit anterior
git -C ../atlasgym_front checkout <commit>
./scripts/actualizar.sh --sin-git
```
Si el problema fue de datos (una migración), restaurar el backup que el script hizo justo antes (paso 7).
Después, arreglar en `develop` y volver a `main` con `git checkout main`.

---

## 9. Variante: PC en el local sin IP pública

*Pendiente de la decisión del hosting.* Si el servidor es una PC del gimnasio detrás del router, Caddy no
puede sacar el certificado porque nadie de afuera llega a los puertos 80/443. La alternativa es
**Cloudflare Tunnel**: un programa en la PC abre la conexión hacia Cloudflare y Cloudflare pone el HTTPS.
Cambia el paso 2 (no se abren puertos) y Caddy pasa a servir HTTP interno. Se documenta cuando se elija.

---

## 10. Si algo falla

```bash
docker compose ps                        # qué contenedores están arriba
docker compose logs --tail 100 backend   # o frontend, mysql, caddy
```

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El navegador dice "conexión no segura" | El DNS no apunta al servidor o los puertos 80/443 están cerrados | Revisar paso 2 y `docker compose logs caddy` |
| El backend se reinicia en bucle | Falta una variable obligatoria o una migración no coincide | `logs backend`: el error dice qué variable o qué tabla |
| El login no deja entrar a un titular | Clave mal copiada del `.env` | Revisar que no haya espacios de más; las claves distinguen mayúsculas |
| La tablet dice "PIN incorrecto" | Se cambió `ATLAS_TOTEM_PIN` pero no se reconstruyó el front | `./scripts/actualizar.sh --sin-git` |
| El tótem no registra ingresos | Clave de terminal distinta a `ATLAS_TOTEM_SECRET` | Desvincular la tablet y volver a vincularla (paso 6.4) |
| Un socio no puede entrar | Todavía no tiene acceso u olvidó su clave | Ficha del socio → **Generar clave temporal** |
| `actualizar.sh` dice "valores de ejemplo" | Quedó un `CAMBIAR_...` en el `.env` | Reemplazarlo por un valor generado (paso 4) |

---

## Notas de seguridad

- El **PIN del tótem** es una traba liviana: viaja dentro de la página y alguien con conocimientos lo puede leer.
  La protección real es `ATLAS_TOTEM_SECRET`, que valida el servidor en cada ingreso.
- No compartir el `.env` por WhatsApp ni mail. Si se filtra, generar claves nuevas y relanzar
  (cambiar `ATLAS_JWT_SECRET` cierra la sesión de todos).
- MySQL, el backend y el frontend nunca se exponen a Internet; todo entra por Caddy.
