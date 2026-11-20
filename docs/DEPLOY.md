# Guía de deploy — ATLAS Gym

Paso a paso para instalar ATLAS en el servidor del gimnasio, dejarlo listo para el primer día y mantenerlo andando.
Está escrita para que la siga cualquiera del equipo, aunque nunca haya hecho un deploy.

> **Regla de oro:** este repo es público. Ninguna clave, nombre de titular, teléfono, alias de cobro ni el
> archivo `.env` se sube a Git. Todo eso vive **solo en el servidor** (y en el gestor de claves del equipo).

---

## 0. Qué se instala

```
Visitante ──HTTPS──► Cloudflare ◄──túnel saliente── cloudflared ──► Caddy (HTTP, solo interno)
                                                                     ├── /api/*  ──► backend  (Spring Boot, :8080)
                                                                     └── resto   ──► frontend (Next.js, :3000) ──► backend
                                                                                       backend ──► MySQL 8 (:3307)
```

Son 5 contenedores de Docker definidos en `docker-compose.yml`. El servidor **no tiene puertos web
abiertos**: `cloudflared` abre una conexión saliente hacia Cloudflare y los visitantes entran por ahí.
Cloudflare pone el HTTPS, esconde la IP del servidor y frena tráfico basura (plan gratis).
Caddy, la base, el back y el front escuchan únicamente dentro del servidor (el CI lo verifica en cada PR,
también en modo túnel).

> **Modo directo (sin túnel):** también se puede exponer Caddy en 80/443 con su propio certificado.
> Es lo que usa el ensayo del CI. Ver el final del paso 2.1.

---

## 1. Qué hace falta

### El servidor
| Requisito | Mínimo | Recomendado |
|---|---|---|
| Sistema | Ubuntu 22.04 / 24.04 (Linux de 64 bits), **limpio y sin panel** (nada de Plesk, cPanel, CyberPanel ni plantillas "con Docker") | — |
| Memoria | 2 GB (bajando los `*_MEM_LIMIT` del `.env`) | 4 GB (MySQL + Java + Node juntos; cada contenedor tiene un techo de memoria) |
| Disco | 20 GB | 40 GB (los backups ocupan) |
| Red | Salida a Internet (el túnel no necesita puertos abiertos ni IP pública) | — |
| Software | Docker Engine + plugin `docker compose`, `git`, `curl` | — |

> **Hosting decidido:** VPS de Hostinger (KVM 2) a nombre del gimnasio, dominio con DNS en Cloudflare
> (plan gratis) y túnel de Cloudflare. Como el túnel no necesita IP pública, la guía sirve igual para una
> PC en el local.
>
> Si el servidor ya trae algo escuchando en el puerto 80 o 443 (`sudo ss -ltnp | grep -E ':(80|443) '`
> muestra un proceso), el `docker compose up` falla con "port is already allocated". Con el túnel no hace
> falta ese puerto: en el `.env` poner `CADDY_HTTP_PORT=8081` y `CADDY_HTTPS_PORT=8443`.

### Antes del día de la instalación (pedirle al gimnasio)
- [ ] **Dominio** (ej. `gym.ejemplo.com.ar`) ya agregado a la cuenta de **Cloudflare del gimnasio**
      (nameservers apuntando a Cloudflare) y nosotros como miembros con rol Administrator.
- [ ] **Mail de cada titular** (es su usuario para entrar).
- [ ] **Planes y precios** vigentes.
- [ ] **Excel de socios** (ver paso 6.3).
- [ ] **Alias / CBU de cobro y titular de la cuenta** (se cargan desde la app, nunca en el código).
- [ ] **PIN de 4 dígitos** que quieran para la tablet del tótem.

### Antes del día (en el código, por PR)
- [ ] El contacto de privacidad está completo en `lib/datos-legales.ts` del front (`contactoPrivacidad`).
      Si falta, `/privacidad` muestra "datos pendientes".
- [ ] Lo que se va a instalar está en `main` en **los dos repos** (paso 8: "Publicar una versión").

### El día de la instalación, en orden

Cada paso tiene su control: no pasar al siguiente hasta que dé lo esperado.

| # | Paso | Control |
|---|---|---|
| 1 | Servidor preparado (paso 2) y reiniciado | `timedatectl` dice `America/Argentina/Buenos_Aires`; como `atlas`, `docker ps` no da error |
| 2 | Sin `cloudflared` del sistema (paso 2.1) | `systemctl is-active cloudflared` → `inactive` |
| 3 | Túnel creado y Cloudflare ajustado (paso 2.1) | Public Hostname → `caddy:80`; Email Obfuscation y Bot Fight Mode en Off |
| 4 | Los dos repos en `main`, al día (paso 3) | `git log -1 --oneline` en cada repo = último commit de `main` en GitHub |
| 5 | `.env` completo (paso 4) | `grep -c CAMBIAR .env` → `0`; `ls -l .env` → `-rw-------` |
| 6 | Copia del `.env` guardada en el gestor de claves | La ve otra persona del equipo |
| 7 | rclone con Google Drive (paso 4.1) | `rclone lsd gdrive:` lista `ATLAS-backups` |
| 8 | healthchecks.io (paso 4.2) | `grep BACKUP_PING_URL .env` tiene una URL |
| 9 | Sistema levantado (paso 5) | `docker compose ps`: todo `Up` / `healthy`; `curl -s localhost:8080/api/salud` → `"estado":"ok"` |
| 10 | Túnel y red (paso 5) | Cloudflare: **Healthy**, un solo conector; la red de Docker en `172.16-31.x` |
| 11 | Desde un celular **con datos móviles** (fuera del Wi-Fi del gimnasio) | `https://<dominio>/login` carga y muestra la versión instalada |
| 12 | Titulares, configuración e importación (pasos 6.1 a 6.3) | Los dos entraron y cambiaron su clave; planes y alias cargados |
| 13 | Alta inicial apagada (paso 5) | `grep BOOTSTRAP .env` → `false`; el back reiniciado |
| 14 | Tablet, control final y backups (pasos 6.4, 6.5 y 7) | Backup manual en Drive, check verde en healthchecks.io, `crontab -l` con la línea del backup |

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

# 3. Preparar el servidor (deja ufw solo con SSH; con el túnel no hace falta abrir 80/443):
sudo ./scripts/preparar-servidor.sh
```

El script deja configurados automáticamente:
1. Zona horaria `America/Argentina/Buenos_Aires`.
2. Docker Engine + plugin `docker compose`, `git`, `curl` y `rclone`.
3. Swap de 2 GB (`/swapfile`) si el servidor no tenía swap activa.
4. Usuario `atlas` en el grupo `docker` y carpeta `/opt/atlas` de su propiedad.
5. Firewall `ufw`: permite el puerto real de SSH (`sshd -T`) por defecto, o SSH + 80/443 si se pasa `--abrir-web`.
   > **Ojo:** Docker publica puertos por encima de `ufw`. Por eso `docker-compose.yml` publica Caddy solo en
   > `127.0.0.1` (`CADDY_BIND`): eso es lo que de verdad cierra 80/443, no el firewall.
6. Parches de seguridad automáticos (`unattended-upgrades`) y `fail2ban` para SSH.
7. SSH solo con llave (`/etc/ssh/sshd_config.d/00-atlas-hardening.conf`: `PasswordAuthentication no`, `PermitRootLogin prohibit-password`, verificado con `sshd -T`) **únicamente si** ya hay al menos una llave en `authorized_keys` de `root` o de `atlas` (si no hay ninguna, no toca SSH y avisa en pantalla para no dejarte afuera).

### Lo que sigue siendo manual
- **Llave SSH:** si al crear el VPS no cargaste tu llave pública SSH, agregala en `/home/atlas/.ssh/authorized_keys` (o `/root/.ssh/authorized_keys`) y volvé a correr `sudo ./scripts/preparar-servidor.sh` para deshabilitar el login por contraseña.
- **Reiniciar el servidor:** `sudo reboot` al terminar el script. `cron` toma la zona horaria recién al
  arrancar (sin reiniciar, el backup de las 03:30 corre a la hora UTC) y el usuario `atlas` recién queda
  en el grupo `docker` en una sesión nueva.
- **Túnel de Cloudflare:** ver el paso 2.1.
- **Usar el usuario `atlas`:** para los pasos siguientes, entrar como `atlas` (`su - atlas` o por SSH con `atlas@<ip>`).
  Comprobar con `docker ps`: si dice "permission denied", salir y volver a entrar.

### 2.1 Crear el túnel de Cloudflare (una sola vez, desde el navegador)

1. En el panel de Cloudflare del gimnasio: **Zero Trust > Networks > Tunnels > Create a tunnel**, tipo
   **Cloudflared**, nombre `atlas`.
2. En "Install connector" elegir **Docker** y copiar **solo el token** (la cadena larga después de
   `--token`). No correr el comando que muestra: el contenedor ya está en `docker-compose.yml`.
   El token va en `CLOUDFLARE_TUNNEL_TOKEN` del `.env` (paso 4). Es una clave: no compartirlo.
   Para volver a verlo más adelante: **Networks > Tunnels > atlas > Configure** (el comando de instalación lo trae).
   > **No instalar `cloudflared` en el sistema** (`cloudflared service install`, el paquete `.deb`, etc.). Si ya
   > está, desactivarlo: `sudo systemctl disable --now cloudflared`. Con dos conectores del mismo túnel,
   > Cloudflare reparte las visitas entre los dos, y el del sistema no llega a `caddy:80` (está fuera de la
   > red de Docker): la mitad de los pedidos dan **502** al azar. `systemctl is-active cloudflared` tiene que decir `inactive`.
3. En **Public Hostname** agregar:
   - Subdomain: vacío (o el que se quiera); Domain: el del gimnasio.
   - Service: tipo **HTTP**, URL **`caddy:80`**.

   Cloudflare crea solo el registro DNS. No hace falta registro A ni la IP del servidor.
4. En **SSL/TLS > Edge Certificates** activar **Always Use HTTPS**.
5. Apagar lo que rompe la app: **Scrape Shield > Email Address Obfuscation: Off** (reescribe los mails del
   HTML y puede romper la carga de la página) y **Security > Bots > Bot Fight Mode: Off** (puede frenar al tótem).

El túnel aparece "Healthy" recién cuando el sistema esté levantado (paso 5).

**Modo directo (sin túnel):** en el `.env` borrar `COMPOSE_PROFILES` y `CLOUDFLARE_TUNNEL_TOKEN`, poner
`ATLAS_CADDY_SITIO=<dominio>` y `CADDY_BIND=0.0.0.0`, correr `preparar-servidor.sh --abrir-web` y crear un
registro **A** del dominio a la IP del servidor. Caddy saca el certificado solo.

---

## 3. Descargar el código del front

`atlasgym_back` ya quedó clonado en `/opt/atlas/atlasgym_back` en el paso 2. Los dos repos van en **carpetas hermanas**, así que (entrando como `atlas`) solo falta clonar `atlasgym_front` al lado:

```bash
cd /opt/atlas
git clone --branch main https://github.com/Olaiz9/atlasgym_front.git
cd atlasgym_back
```

Los dos repos tienen que estar en la **misma rama** (`main` para una instalación real; `develop` solo para un
ensayo) y al día. `actualizar.sh` hace `git pull` de la rama en la que esté parado cada repo:

```bash
git -C /opt/atlas/atlasgym_back  checkout main && git -C /opt/atlas/atlasgym_back  pull --ff-only
git -C /opt/atlas/atlasgym_front checkout main && git -C /opt/atlas/atlasgym_front pull --ff-only
git -C /opt/atlas/atlasgym_back log -1 --oneline; git -C /opt/atlas/atlasgym_front log -1 --oneline
```

Los dos últimos commits tienen que coincidir con los de esa rama en GitHub.

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
| `ATLAS_TOTEM_HORARIO` | Horario de apertura del gimnasio, ej. `07:00-22:30`. Fuera de ese horario no se avisa si la tablet está apagada |
| `CLOUDFLARE_TUNNEL_TOKEN` | El token del túnel (paso 2.1) |
| `ATLAS_DOMAIN` | El dominio, sin `https://` (ej. `gym.ejemplo.com.ar`) |
| `ATLAS_CORS_ALLOWED_ORIGIN` | `https://` + el dominio (ej. `https://gym.ejemplo.com.ar`) |

> **Antes del primer arranque, tres cosas que después no se arreglan fácil:**
> - **Claves solo con letras y números.** `docker compose` interpreta `$`, `#`, comillas y espacios del
>   `.env`, y la clave que llega al sistema no es la que escribiste. Las de `openssl rand -hex` ya cumplen;
>   cuidado con las claves temporales de los titulares, que se eligen a mano.
> - **No cambiar `DB_PASSWORD` ni `MYSQL_ROOT_PASSWORD` después del primer arranque.** MySQL guarda las
>   claves en su volumen la primera vez; si se cambian en el `.env`, el back ya no puede conectarse. Solo se
>   arregla volviendo a la clave anterior o con `docker compose down -v`, que **borra la base entera**.
> - **Revisar dos veces los mails de los titulares.** Son su usuario. El alta inicial no corrige una cuenta
>   que ya existe: un mail mal escrito queda como una cuenta de más.

`COMPOSE_PROFILES=tunel`, `ATLAS_CADDY_SITIO=:80` y `CADDY_BIND=127.0.0.1` ya vienen puestos para el
túnel; `actualizar.sh` se frena si falta el token o si el sitio no es `:80`. El resto se deja como viene. Guardar una copia del `.env` en el gestor de claves del equipo: sin él,
los backups no se pueden restaurar con las mismas credenciales.

### 4.1 Copia fuera del servidor en Google Drive (obligatoria)

Si se rompe el disco del servidor, se pierden también los backups locales. La copia va al
Google Drive **de la cuenta del gimnasio**. Si no hay copia externa configurada (`RCLONE_REMOTE` o
`BACKUP_OFFSITE_DIR`), o si la subida falla, el backup **termina con error** (el local queda igual) y
el monitor del paso 4.2 avisa. Hasta configurarla, `actualizar.sh` tampoco puede hacer su backup previo (si el primer arranque falla a medias y se reintenta, o si se corre con `--sin-git`, necesita que la copia externa ya esté lista).

1. En Google Drive (cuenta del gimnasio), crear la carpeta `ATLAS-backups`.
2. En el servidor, como el usuario que corre el cron (`atlas`):
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

> Las credenciales de rclone quedan en `~/.config/rclone/rclone.conf` del servidor. **Nunca** van al repo.

### 4.2 Aviso si un día no hubo backup (healthchecks.io, gratis — obligatorio)

1. Crear cuenta en healthchecks.io con el mail del gimnasio y un check "Backup ATLAS":
   período **1 día**, gracia **2 horas**. Agregar como aviso el mail de los titulares y el del equipo.
2. Copiar la URL del check (`https://hc-ping.com/...`) en el `.env`: `BACKUP_PING_URL=...`
3. Cada backup que termina bien avisa a esa URL; si falla, avisa a `<url>/fail`. Si un día no llega
   ningún aviso (servidor apagado, cron roto), healthchecks.io manda un mail igual.

---

## 5. Levantar el sistema

```bash
./scripts/actualizar.sh
```

El script hace todo en orden y **se frena si algo está mal**:

1. Revisa el `.env`: corta si quedó algún `CAMBIAR_...`, si falta el secreto del tótem o si el PIN está vacío, no tiene 4 dígitos o es `1234`. Avisa si todavía falta la copia de backups fuera del servidor (paso 4.1) o el monitor de avisos (paso 4.2).
2. Si la base ya existe, hace un **backup antes de tocar nada**.
3. Trae el código nuevo de los dos repos (`git pull`).
4. Construye y levanta los contenedores (la primera vez tarda 5–10 minutos).
5. Espera a que todo responda.

Al terminar, abrir `https://<dominio>/login`: abajo tiene que decir `ATLAS Gym Platform · <versión> · <commit>`.
En el panel de Cloudflare el túnel tiene que figurar **Healthy** y con **un solo conector** (si hay dos, ver
el aviso del paso 2.1). Si no carga, ver los logs del túnel: `docker compose logs --tail 50 cloudflared`.

Comprobar también que la red interna de Docker quedó dentro de `172.16.0.0/12` (el `Caddyfile` solo confía en
esa red para leer la IP real de cada visitante; si quedara afuera, todos compartirían la misma IP y 5 logins
fallidos bloquearían a todo el gimnasio):

```bash
docker network inspect atlasgym_back_default --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}'
# Tiene que empezar con 172.16. a 172.31. (por ejemplo 172.18.0.0/16)
```

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
- [ ] Dejar la tablet en **modo kiosco** (fijar la pantalla en el navegador) y avisar a los titulares:
      **si se corta Internet, no recargar la página del tótem.** Sigue marcando ingresos y los sube solos al
      volver la señal, pero en esta versión una página recargada sin Internet no abre (tampoco anclada a la
      pantalla de inicio) hasta que vuelva la conexión.
- [ ] Probar el aviso: con el Wi-Fi de la tablet apagado, a los 5 minutos la app de los titulares tiene que mostrar
      **"El tótem no tiene señal"**. Al prender el Wi-Fi, el cartel se va solo en menos de un minuto.

### 6.5 Control final
- [ ] Cobrar una cuota de prueba y **anularla** (con motivo). Revisar que el arqueo del día la excluya.
- [ ] `https://<dominio>/privacidad` carga sin el cartel de "datos pendientes".
- [ ] A un socio de prueba: ficha → **Generar clave temporal**; entra con su DNI y esa clave, y la app le pide elegir una propia.
- [ ] Backups automáticos programados (paso 7) y **un backup manual** hecho al terminar el día.

---

## 7. Backups

El sistema guarda plata y datos de personas: **sin backups no se entrega**. La copia externa en Google Drive (`RCLONE_REMOTE`, paso 4.1) y el aviso en healthchecks.io (`BACKUP_PING_URL`, paso 4.2) ya quedaron configurados antes del primer arranque.

```bash
crontab -e
# Todos los días a las 03:30
30 3 * * * /opt/atlas/atlasgym_back/scripts/backup-mysql.sh >> /opt/atlas/backup.log 2>&1
```

- Correr un backup a mano (`./scripts/backup-mysql.sh`) y verificar que el archivo aparezca en Google Drive (`ATLAS-backups`) y que el check de healthchecks.io quede en verde.
- A la mañana siguiente del deploy, revisar que `/opt/atlas/backup.log` termine en `Backup terminado OK`.
- Se guardan en `backups/`, comprimidos, y se borran solos los de más de 14 días.
- **Restaurar** (hace un backup de seguridad antes de pisar nada):
  ```bash
  ./scripts/restore-mysql.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
  ```

### 7.1 Una vez por mes: comprobar que el backup sirve

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

Funciona igual que el VPS: el túnel sale desde la PC hacia Cloudflare, así que no hace falta IP pública
ni abrir puertos en el router. Se siguen los mismos pasos (Ubuntu en la PC, paso 2.1 incluido).

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

El plan gratis alcanza: avisa por **mail** y por la app de UptimeRobot, y chequea cada 5 minutos. Telegram y Slack son del plan pago (Solo en adelante); si el equipo quiere el aviso en un chat sin pagar, el plan gratis incluye Discord y Google Chat ([precios](https://uptimerobot.com/pricing), [integraciones](https://help.uptimerobot.com/en/articles/11361285-integrations-basic-information)).

---

## 11. Si algo falla

```bash
docker compose ps                        # qué contenedores están arriba
docker compose logs --tail 100 backend   # o frontend, mysql, caddy, cloudflared
```

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| La web no carga (error 1033 o 502 de Cloudflare) | El túnel no está conectado o apunta mal | Panel de Cloudflare: el túnel tiene que estar "Healthy" y el destino ser `caddy:80` (paso 2.1). Ver `docker compose logs cloudflared` |
| A veces carga y a veces da 502 | Hay dos conectores del túnel (el del sistema o un servidor viejo con el mismo token) | `sudo systemctl disable --now cloudflared`; si es otro servidor, apagarlo o rotar el token (paso 12) |
| El backend no conecta a MySQL ("Access denied") después de tocar el `.env` | Se cambió `DB_PASSWORD` o `MYSQL_ROOT_PASSWORD` después del primer arranque | Volver a poner la clave anterior (está en la copia del `.env` del gestor de claves) |
| `docker compose up` dice "port is already allocated" | Otro programa usa el 80 o el 443 | `CADDY_HTTP_PORT=8081` y `CADDY_HTTPS_PORT=8443` en el `.env` (paso 1) |
| La web se ve rota o en blanco y la consola habla de "hydration" | Email Obfuscation de Cloudflare prendido | Apagarlo (paso 2.1, punto 5) |
| Nadie puede entrar y dice "demasiados intentos" | El back no recibe la IP real de cada visitante | Revisar que el destino del túnel sea `caddy:80` y no `frontend:3000` (paso 2.1) |
| El backend se reinicia en bucle | Falta una variable obligatoria o una migración no coincide | `logs backend`: el error dice qué variable o qué tabla |
| El login no deja entrar a un titular | Clave mal copiada del `.env` | Revisar que no haya espacios de más; las claves distinguen mayúsculas |
| La tablet dice "PIN incorrecto" | Se cambió `ATLAS_TOTEM_PIN` pero no se reconstruyó el front | `./scripts/actualizar.sh --sin-git` |
| El tótem no registra ingresos | Clave de terminal distinta a `ATLAS_TOTEM_SECRET` | Desvincular la tablet y volver a vincularla (paso 6.4) |
| Un socio no puede entrar | Todavía no tiene acceso u olvidó su clave | Ficha del socio → **Generar clave temporal** |
| `actualizar.sh` dice "valores de ejemplo" | Quedó un `CAMBIAR_...` en el `.env` | Reemplazarlo por un valor generado (paso 4) |

---

## 12. Servidor perdido (se rompió, lo borraron o hay que mudarse)

Los datos sobreviven en el Google Drive del gimnasio (`ATLAS-backups`). Para levantar todo en un servidor nuevo hace falta:

- La **copia del `.env`** del gestor de claves (con las mismas claves de MySQL, JWT y tótem: así la tablet sigue vinculada).
- Acceso a la **cuenta de Google del gimnasio** (para autorizar rclone) y a la de **Cloudflare** (para el túnel).
- Saber **qué versión** estaba instalada (el tag, ej. `v1.0.1`): anotarla en el gestor de claves junto al `.env` en cada actualización.

Pasos:

1. **Primero cortar el servidor viejo**, si todavía existe: apagarlo, o en Cloudflare **rotar el token del túnel**
   (en la página del túnel `atlas`, en Networks > Tunnels; el token nuevo va al `.env` del servidor nuevo).
   Si el viejo revive con el mismo token, Cloudflare
   reparte las visitas entre los dos y la mitad de los cobros irían a una base vieja.
2. En el servidor nuevo: pasos 2 a 4 de esta guía, pero con el `.env` **copiado** (no uno nuevo) y el token
   del túnel actual.
3. Levantar **la misma versión** que tenía el backup, no una más nueva: `./scripts/actualizar.sh --version vX.Y.Z`.
4. Bajar el último backup de Drive y restaurarlo:
   ```bash
   rclone lsl gdrive:ATLAS-backups | sort -k2,3 | tail -3      # los más recientes
   rclone copy gdrive:ATLAS-backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz backups/
   ./scripts/restore-mysql.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
   ```
5. Comprobar con los titulares: socios, últimos cobros y arqueo del último día. **Lo cargado después de ese
   backup se perdió**: hay que volver a cargarlo a mano (cobros del día desde los comprobantes o el cuaderno).
6. Recién con todo verificado, actualizar a la versión más nueva si hace falta (paso 8), y volver a programar el
   cron de backups (paso 7).

---

## Notas de seguridad

- El **PIN del tótem** es una traba liviana: viaja dentro de la página y alguien con conocimientos lo puede leer.
  La protección real es `ATLAS_TOTEM_SECRET`, que valida el servidor en cada ingreso.
- No compartir el `.env` por WhatsApp ni mail. Si se filtra, generar claves nuevas y relanzar
  (cambiar `ATLAS_JWT_SECRET` cierra la sesión de todos).
- MySQL, el backend y el frontend nunca se exponen a Internet; todo entra por Cloudflare y Caddy.
- La IP real de cada visitante (para el límite de intentos de login) sale del header `CF-Connecting-IP`.
  Caddy solo lo cree si el pedido viene de la red interna de Docker, o sea de `cloudflared`.
