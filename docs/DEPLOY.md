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
| Memoria | 2 GB (bajando los `*_MEM_LIMIT` del `.env`) | 4 GB (MySQL + Java + Node juntos; cada contenedor tiene un techo de memoria) |
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

En un Ubuntu 22.04 / 24.04 recién creado, instalar primero `git`, clonar `atlasgym_back` en `/opt/atlas` y correr el script `scripts/preparar-servidor.sh` (es **idempotente**: se puede correr más de una vez sin romper ni duplicar nada):

```bash
# 1. Instalar git (puede no venir de fábrica), clonar el backend en /opt/atlas y entrar al repo:
sudo apt-get update && sudo apt-get install -y git
sudo mkdir -p /opt/atlas && cd /opt/atlas
sudo git clone https://github.com/Olaiz9/atlasgym_back.git
cd /opt/atlas/atlasgym_back

# 2. Opcional: ver qué haría sin modificar nada
./scripts/preparar-servidor.sh --simular

# 3. Preparar el servidor (deja ufw solo con SSH; agregar --abrir-web si se van a exponer 80 y 443 directo):
sudo ./scripts/preparar-servidor.sh --abrir-web
```

El script deja configurados automáticamente:
1. Zona horaria `America/Argentina/Buenos_Aires`.
2. Docker Engine + plugin `docker compose`, `git`, `curl` y `rclone`.
3. Swap de 2 GB (`/swapfile`) si el servidor no tenía swap activa.
4. Usuario `atlas` en el grupo `docker` y carpeta `/opt/atlas` de su propiedad.
5. Firewall `ufw`: permite el puerto real de SSH (`sshd -T`) por defecto, o SSH + 80/443 si se pasa `--abrir-web`.
   > **Ojo:** Docker publica 80/443 de Caddy aunque `ufw` no los permita. Para cerrarlos de verdad (ej. con Cloudflare Tunnel) hay que dejar de publicarlos en `docker-compose.yml`.
6. Parches de seguridad automáticos (`unattended-upgrades`) y `fail2ban` para SSH.
7. SSH solo con llave (`/etc/ssh/sshd_config.d/00-atlas-hardening.conf`: `PasswordAuthentication no`, `PermitRootLogin prohibit-password`, verificado con `sshd -T`) **únicamente si** ya hay al menos una llave en `authorized_keys` de `root` o de `atlas` (si no hay ninguna, no toca SSH y avisa en pantalla para no dejarte afuera).

### Lo que sigue siendo manual
- **Llave SSH:** si al crear el VPS no cargaste tu llave pública SSH, agregala en `/home/atlas/.ssh/authorized_keys` (o `/root/.ssh/authorized_keys`) y volvé a correr `sudo ./scripts/preparar-servidor.sh` para deshabilitar el login por contraseña.
- **DNS** (si se exponen los puertos 80 y 443): en el proveedor del dominio, crear un registro **A** que apunte el dominio a la IP pública del servidor. Comprobar antes de seguir (tiene que devolver la IP del servidor):
  ```bash
  getent hosts gym.ejemplo.com.ar
  ```
  > Sin este paso Caddy no puede sacar el certificado HTTPS.
- **Usar el usuario `atlas`:** para los pasos siguientes, entrar como `atlas` (`su - atlas` o por SSH con `atlas@<ip>`).

---

## 3. Descargar el código del front

`atlasgym_back` ya quedó clonado en `/opt/atlas/atlasgym_back` en el paso 2. Los dos repos van en **carpetas hermanas**, así que (entrando como `atlas`) solo falta clonar `atlasgym_front` al lado:

```bash
cd /opt/atlas
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
- [ ] Alumnos → **Importar** → **Revisar**: el sistema hace un simulacro (no guarda nada) y muestra cuántos socios
      quedarían al día, pendientes y morosos, y los avisos (planes que no existen, DNI repetidos, mails inválidos,
      cuotas sin monto).
- [ ] **Revisar con los titulares la lista de morosos** antes de confirmar: si alguien figura moroso y pagó, corregir
      el "Último mes pago" en el Excel y volver a revisar. Recién cuando la lista esté bien, **Confirmar importación**.
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
- **Restaurar** (hace un backup de seguridad antes de pisar nada):
  ```bash
  ./scripts/restore-mysql.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
  ```

### 7.1 Copia fuera del servidor en Google Drive (obligatoria)

Si se rompe el disco del servidor, se pierden también los backups locales. La copia va al
Google Drive **de la cuenta del gimnasio**. Si `RCLONE_REMOTE` está configurado y la subida falla,
el backup **termina con error** (el local queda igual) y el monitor del paso 7.2 avisa.

1. En Google Drive (cuenta del gimnasio), crear la carpeta `ATLAS-backups`.
2. En el servidor, como el usuario que corre el cron:
   ```bash
   rclone config
   ```
   - `n` (nuevo remoto) → nombre: `gdrive` → tipo: `drive` (Google Drive).
   - `client_id` y `client_secret`: dejar vacíos. `scope`: `1` (acceso completo).
   - "Use web browser to automatically authenticate?": **`n`** (el servidor no tiene navegador).
     rclone muestra un comando `rclone authorize "drive" ...`: correrlo en una PC con rclone y
     navegador, iniciar sesión **con la cuenta del gimnasio**, y pegar en el servidor el código que devuelve.
   - "Configure this as a Shared Drive?": `n`. Confirmar y salir con `q`.
3. Probar: `rclone lsd gdrive:` tiene que listar `ATLAS-backups`.
4. En el `.env`: `RCLONE_REMOTE=gdrive:ATLAS-backups`
5. Correr un backup a mano (`./scripts/backup-mysql.sh`) y ver el archivo en Drive.

> Las credenciales de rclone quedan en `~/.config/rclone/rclone.conf` del servidor. **Nunca** van al repo.

### 7.2 Aviso si un día no hubo backup (healthchecks.io, gratis)

1. Crear cuenta en healthchecks.io con el mail del gimnasio y un check "Backup ATLAS":
   período **1 día**, gracia **2 horas**. Agregar como aviso el mail de los titulares y el del equipo.
2. Copiar la URL del check (`https://hc-ping.com/...`) en el `.env`: `BACKUP_PING_URL=...`
3. Cada backup que termina bien avisa a esa URL; si falla, avisa a `<url>/fail`. Si un día no llega
   ningún aviso (servidor apagado, cron roto), healthchecks.io manda un mail igual.

### 7.3 Una vez por mes: comprobar que el backup sirve

```bash
./scripts/verificar-backup.sh            # el último backup diario
./scripts/verificar-backup.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
```

Restaura el backup en un MySQL **temporal y separado** (no toca la base real ni abre puertos),
muestra cuántos usuarios, alumnos, planes, pagos, movimientos de caja y asistencias tiene, y borra
el contenedor. Si el archivo está roto o no se restaura, termina con error. Los números tienen que
parecerse a los del sistema. El CI hace esta misma prueba en cada PR.

---

## 8. Publicar una versión nueva

El servidor siempre corre versiones etiquetadas (`vMAYOR.MENOR.PARCHE`, ej. `v1.0.0`) sobre **`main`**. El trabajo del día a día va a `develop`.

1. **Release (`develop → main`):** cuando `develop` está estable, abrir un PR `develop → main` en **cada repo** que cambió (subiendo la versión en `package.json` del front si corresponde, ej. `1.0.0` → `1.1.0`), con CI en verde y aprobación, igual que cualquier PR.
2. **Tag en los dos repos:** una vez mergeado en `main`, etiquetar con el **mismo tag** `vMAYOR.MENOR.PARCHE` tanto `atlasgym_back` como `atlasgym_front`:
   ```bash
   # En atlasgym_back y en atlasgym_front (parado en main actualizado):
   git checkout main && git pull
   git tag v1.1.0
   git push origin v1.1.0
   ```
3. **En el servidor**, fuera del horario pico:
   ```bash
   cd /opt/atlas/atlasgym_back
   ./scripts/actualizar.sh --version v1.1.0
   ```
   El script verifica que el tag exista en los dos repos antes de tocar nada, hace un backup previo de la base, guarda en `.version-anterior` la versión previa de ambos repos (`back=` y `front=`, como tag exacto o SHA completo) junto con la ruta exacta de ese backup (`backup=`), cambia ambos repos al tag y reconstruye los contenedores.
   > **Nota:** después de usar `--version`, los repositorios quedan parados sobre ese tag (*detached HEAD*). A partir de ahí, para actualizar siempre hay que indicar la nueva versión con `./scripts/actualizar.sh --version vX.Y.Z` (si se corre `./scripts/actualizar.sh` sin argumentos, el script corta avisando `"Estás en una versión etiquetada (<tag>). Usá --version vX.Y.Z para actualizar."`).
4. Comprobar que el commit del login es el nuevo y hacer un ingreso de prueba.

**Si la versión nueva sale mal (vuelta atrás en un comando):**
```bash
cd /opt/atlas/atlasgym_back
./scripts/actualizar.sh --volver
```
Esto vuelve cada repositorio a la versión exacta guardada en `.version-anterior` (`back=` y `front=`) y relanza los contenedores.
Si la versión nueva traía una migración de base de datos, restaurar además el backup que el script hizo justo antes de actualizar (la ruta exacta guardada en `backup=` aparece en pantalla al correr `--volver`, ver paso 7):
```bash
./scripts/restore-mysql.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
```
> **Atención:** Restaurar el backup previo BORRA los cobros, altas y asistencias registrados desde la actualización. Antes de restaurar, anotá o exportá lo que se cargó en ese lapso.

---

## 9. Variante: PC en el local sin IP pública

*Pendiente de la decisión del hosting.* Si el servidor es una PC del gimnasio detrás del router, Caddy no
puede sacar el certificado porque nadie de afuera llega a los puertos 80/443. La alternativa es
**Cloudflare Tunnel**: un programa en la PC abre la conexión hacia Cloudflare y Cloudflare pone el HTTPS.
Cambia el paso 2 (no se abren puertos) y Caddy pasa a servir HTTP interno. Se documenta cuando se elija.

---

## 10. Monitoreo (UptimeRobot)

Para detectar caídas del servidor o de la base de datos antes de que los titulares o los socios lo noten, se configura un monitor externo gratuito en **UptimeRobot** apuntando al endpoint de salud.

### 10.1 Configuración del monitor
1. Crear una cuenta gratuita en [uptimerobot.com](https://uptimerobot.com).
2. En el panel principal, hacer clic en **+ Add New Monitor**:
   - **Monitor Type:** `HTTP(s)` (o `Keyword` si se desea validar que el cuerpo contenga `"estado":"ok"`).
   - **Friendly Name:** `ATLAS Gym - Backend y Base`
   - **URL (or IP):** `https://<dominio>/api/salud` (ej. `https://gym.ejemplo.com.ar/api/salud`).
   - **Monitoring Interval:** `5 minutes` (intervalo estándar del plan gratuito).
   - **Monitor Timeout:** `30 seconds`.

El endpoint `/api/salud` es público, ejecuta un `SELECT 1` en la base de datos y responde:
- **HTTP 200** si el backend y la base responden correctamente (`{"estado":"ok","base":"ok",...}`).
- **HTTP 503** si la base de datos no está disponible (`{"estado":"error","base":"error",...}`), lo que activa la alerta en UptimeRobot.

### 10.2 Configuración de alertas y canales
En **My Settings → Alert Contacts**:
1. **Email:** Agregar el correo de cada titular (`ATLAS_ADMIN_EMAIL`, `ATLAS_ADMIN2_EMAIL`) y el del equipo técnico.
2. En la configuración del monitor creado, tildar los contactos de alerta creados.

> **Canales y limitaciones según el plan vigente (fuente oficial: [uptimerobot.com/pricing](https://uptimerobot.com/pricing)):**
> - **Plan gratuito:** Incluye alertas por **correo electrónico** (Email) y notificaciones push en la app móvil oficial, con intervalos de chequeo cada 5 minutos.
> - **Canales avanzados (Telegram, Webhooks, Slack, SMS/llamada) o intervalos menores (ej. 60 s):** La disponibilidad de integraciones directas (como Telegram o Webhooks) está sujeta a los términos vigentes del servicio y requiere un plan pago (Solo, Team o PRO). Si la organización cuenta con un plan pago que incluya Telegram, se puede vincular el bot oficial (`@UptimeRobotBot`) como contacto adicional; de lo contrario, utilizar las alertas por email incluidas en el plan gratuito.

---

## 11. Si algo falla

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
