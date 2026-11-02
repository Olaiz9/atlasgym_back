# Smoke test de la API ATLAS Gym contra MySQL 8 real (perfil prod).
# Uso normal: via scripts\smoke-local.ps1 (recrea la base atlas_gym_smoke y levanta el backend).
# Uso directo: .\scripts\smoke-api.ps1 -Base http://127.0.0.1:8080 -Admin1Pass ... -Admin2Pass ... -TotemSecret ...
# ATENCION: crea planes, socios, pagos y asistencias. NUNCA correrlo contra la base real del gimnasio.
param(
    [string]$Base = "http://127.0.0.1:8080",
    [string]$Admin1Email = "titular1@smoke.local",
    [string]$Admin1Pass = "SmokeTitular1!",
    [string]$Admin1Nombre = "Titular 1",
    [string]$Admin2Email = "titular2@smoke.local",
    [string]$Admin2Pass = "SmokeTitular2!",
    [string]$TotemSecret = "secreto-tablet-smoke"
)
$ErrorActionPreference = "Stop"
$script:ok = 0; $script:fail = 0; $script:log = @()

function Call($method, $path, $body = $null, $token = $null, $headers = @{}) {
    $h = @{} + $headers
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $params = @{ Uri = "$Base$path"; Method = $method; Headers = $h; UseBasicParsing = $true; ContentType = "application/json; charset=utf-8" }
    if ($null -ne $body) { $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8 -Compress)) }
    try {
        $r = Invoke-WebRequest @params
        $txt = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        $json = $null; if ($txt) { try { $json = $txt | ConvertFrom-Json } catch {} }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Json = $json; Raw = $txt }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $txt = $_.ErrorDetails.Message }
        else { try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $txt = $sr.ReadToEnd() } catch { $txt = "" } }
        $json = $null; try { $json = $txt | ConvertFrom-Json } catch {}
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = $json; Raw = $txt }
    }
}

function Check($etapa, $desc, [bool]$cond, $detalle = "") {
    if ($cond) { $script:ok++; $m = "  [OK]   $etapa | $desc" } else { $script:fail++; $m = "  [FALLA] $etapa | $desc  -> $detalle" }
    Write-Host $m; $script:log += $m
}

# "Hoy" en hora argentina (UTC-3 fija, sin horario de verano), igual que el servidor.
# Con Get-Date a secas, el CI (que corre en UTC) entre las 21 y las 24 hs de Argentina
# creia que ya era el dia siguiente y fallaban el arqueo y las asistencias de "hoy".
$ahoraAR = [DateTime]::UtcNow.AddHours(-3)
$hoy = $ahoraAR.ToString("yyyy-MM-dd")
$mesActual = $ahoraAR.ToString("yyyy-MM")
$mesSig = $ahoraAR.AddMonths(1).ToString("yyyy-MM")
$totemH = @{ "X-Totem-Secret" = $TotemSecret }

Write-Host "`n=== E4 · Identidad ==="
$l = Call POST "/api/auth/login" @{ email = $Admin1Email; password = $Admin1Pass }
Check "E4" "Login titular 1 -> 200" ($l.Status -eq 200) "status=$($l.Status) $($l.Raw)"
$tok = $l.Json.token
Check "E4" "nombre mostrado = '$Admin1Nombre'" ($l.Json.nombre -eq $Admin1Nombre) "nombre=$($l.Json.nombre)"
Write-Host "        requiereCambioPassword (admin) = $($l.Json.requiereCambioPassword)"
$l2 = Call POST "/api/auth/login" @{ email = $Admin2Email; password = $Admin2Pass }
Check "E4" "Login titular 2 -> 200" ($l2.Status -eq 200) "status=$($l2.Status)"
$tok2 = $l2.Json.token
$bad = Call POST "/api/auth/login" @{ email = "admin@atlasgym.com"; password = "admin123" }
Check "E4" "Login fantasma admin/admin123 bloqueado en prod" ($bad.Status -ge 400) "status=$($bad.Status)"
$sinTok = Call GET "/api/alumnos"
Check "E4" "GET /api/alumnos sin token -> 401/403" ($sinTok.Status -in 401,403) "status=$($sinTok.Status)"

Write-Host "`n=== Setup · Planes y socio ==="
$p3 = Call POST "/api/planes" @{ nombre = "3 Días"; precio = 30000; descripcion = "Smoke"; diaPorSemana = "3"; activo = $true } $tok
Check "SETUP" "Crear plan '3 Días'" ($p3.Status -in 200,201) "status=$($p3.Status) $($p3.Raw)"
$pL = Call POST "/api/planes" @{ nombre = "Libre"; precio = 45000; descripcion = "Smoke"; diaPorSemana = "Libre"; activo = $true } $tok
Check "SETUP" "Crear plan 'Libre'" ($pL.Status -in 200,201) "status=$($pL.Status) $($pL.Raw)"
$planId = $p3.Json.id
$a = Call POST "/api/alumnos" @{ nombre = "Juan Pérez"; dni = "30111222"; email = "juan@test.com"; celular = "011 15-4444-5555"; planId = $planId; activo = $true } $tok
Check "SETUP" "Crear socio Juan Pérez (DNI 30111222)" ($a.Status -in 200,201) "status=$($a.Status) $($a.Raw)"
$alId = $a.Json.id
Write-Host "        estadoCuenta inicial = $($a.Json.estadoCuenta)"

Write-Host "`n=== E5 · Caja ==="
$pg = Call POST "/api/pagos" @{ alumnoId = $alId; planId = $planId; monto = 30000; metodo = "EFECTIVO"; periodoMes = $mesActual; fecha = $hoy; estado = "PAGADO"; cantidadMeses = 1 } $tok
Check "E5" "Cobro cuota $mesActual -> 200/201" ($pg.Status -in 200,201) "status=$($pg.Status) $($pg.Raw)"
$pagoId = $pg.Json.id
Check "E5" "registradoPor = $Admin1Nombre" ($pg.Json.registradoPor -eq $Admin1Nombre) "registradoPor=$($pg.Json.registradoPor)"
$dup = Call POST "/api/pagos" @{ alumnoId = $alId; planId = $planId; monto = 30000; metodo = "EFECTIVO"; periodoMes = $mesActual; fecha = $hoy; estado = "PAGADO"; cantidadMeses = 1 } $tok2
Check "E5" "Cobro DUPLICADO del mismo mes (otro titular) -> 409" ($dup.Status -eq 409) "status=$($dup.Status) $($dup.Raw)"
$par = Call POST "/api/pagos" @{ alumnoId = $alId; planId = $planId; monto = 30000; montoAbonado = 10000; metodo = "TRANSFERENCIA"; periodoMes = $mesSig; fecha = $hoy; estado = "PARCIAL"; cantidadMeses = 1 } $tok2
Check "E5" "Seña PARCIAL `$10.000 de `$30.000 ($mesSig)" ($par.Status -in 200,201 -and $par.Json.estado -eq "PARCIAL") "status=$($par.Status) $($par.Raw)"
$parId = $par.Json.id
$arq1 = Call GET "/api/pagos/arqueo?fecha=$hoy" $null $tok
Write-Host "        Arqueo (antes de completar): $($arq1.Raw)"
Check "E5" "Arqueo suma 30.000 + 10.000 = 40.000" ($arq1.Status -eq 200 -and [decimal]$arq1.Json.totalRecaudado -eq 40000) "status=$($arq1.Status)"
$comp = Call POST "/api/pagos/$parId/completar" @{ metodo = "EFECTIVO" } $tok
Check "E5" "Completar saldo de la seña -> PAGADO" ($comp.Status -eq 200 -and $comp.Json.estado -eq "PAGADO") "status=$($comp.Status) $($comp.Raw)"
$anSin = Call POST "/api/pagos/$pagoId/anular" @{ motivo = "" } $tok
Check "E5" "Anular SIN motivo -> rechazado (400)" ($anSin.Status -eq 400) "status=$($anSin.Status)"
$an = Call POST "/api/pagos/$pagoId/anular" @{ motivo = "Cargado al socio equivocado" } $tok2
Check "E5" "Anular CON motivo -> ANULADO" ($an.Status -eq 200 -and $an.Json.estado -eq "ANULADO") "status=$($an.Status) $($an.Raw)"
$g = Call GET "/api/pagos/$pagoId" $null $tok
Check "E5" "Pago anulado sigue existiendo en la base (auditoría)" ($g.Status -eq 200 -and $g.Json.anuladoPor) "status=$($g.Status) anuladoPor=$($g.Json.anuladoPor)"
$arq2 = Call GET "/api/pagos/arqueo?fecha=$hoy" $null $tok
Write-Host "        Arqueo (final): $($arq2.Raw)"
Check "E5" "Arqueo final excluye el anulado (= 30.000)" ($arq2.Status -eq 200 -and [decimal]$arq2.Json.totalRecaudado -eq 30000) "total=$($arq2.Json.totalRecaudado)"
$re = Call POST "/api/pagos" @{ alumnoId = $alId; planId = $planId; monto = 30000; metodo = "EFECTIVO"; periodoMes = $mesActual; fecha = $hoy; estado = "PAGADO"; cantidadMeses = 1 } $tok
Check "E5" "Tras anular, se puede volver a cobrar el mes" ($re.Status -in 200,201) "status=$($re.Status) $($re.Raw)"

Write-Host "`n=== E3 · Tótem ==="
$tSin = Call POST "/api/asistencias/totem" @{ dni = "30111222" }
Check "E3" "Check-in tótem SIN secreto -> rechazado" ($tSin.Status -in 401,403) "status=$($tSin.Status)"
$t = Call POST "/api/asistencias/totem" @{ dni = "30111222" } $null $totemH
Check "E3" "Check-in tótem CON secreto -> OK" ($t.Status -in 200,201) "status=$($t.Status) $($t.Raw)"
$pcSin = Call GET "/api/asistencias/totem/padron-cache"
Check "E3" "Padrón caché SIN secreto -> rechazado" ($pcSin.Status -in 401,403) "status=$($pcSin.Status)"
$pc = Call GET "/api/asistencias/totem/padron-cache" $null $null $totemH
Check "E3" "Padrón caché CON secreto -> lista mínima" ($pc.Status -eq 200) "status=$($pc.Status)"
Write-Host "        Padrón: $($pc.Raw)"
$evId = [guid]::NewGuid().ToString()
$ayer = $ahoraAR.AddDays(-1).ToString("yyyy-MM-dd")
$sync = @{ eventos = @(@{ clientEventId = $evId; dni = "30111222"; fecha = $ayer; hora = "19:30:00" }) }
$s1 = Call POST "/api/asistencias/totem/sync" $sync $null $totemH
Write-Host "        Sync #1: $($s1.Raw)"
$s2 = Call POST "/api/asistencias/totem/sync" $sync $null $totemH
Write-Host "        Sync #2: $($s2.Raw)"
Check "E3" "Sync offline enviado 2 veces -> ambos 200" ($s1.Status -eq 200 -and $s2.Status -eq 200) "s1=$($s1.Status) s2=$($s2.Status)"

Write-Host "`n=== E2 · Asistencias por fecha ==="
$ah = Call GET "/api/asistencias?fecha=$hoy" $null $tok
Check "E2" "GET ?fecha=hoy -> 1 asistencia" ($ah.Status -eq 200 -and @($ah.Json).Count -eq 1) "status=$($ah.Status) count=$(@($ah.Json).Count)"
$ar = Call GET "/api/asistencias?desde=$ayer&hasta=$hoy" $null $tok
Check "E2" "GET ?desde=ayer&hasta=hoy -> 2 (sync idempotente no duplicó)" ($ar.Status -eq 200 -and @($ar.Json).Count -eq 2) "count=$(@($ar.Json).Count)"

Write-Host "`n=== E6 · Importador CSV ==="
$imp = @{ filas = @(
    @{ nombre = "María Gómez"; dni = "28999888"; celular = "3515551234"; email = ""; planNombre = "3 dias"; ultimoPeriodoPago = $mesActual; montoUltimoPago = 30000; metodoPago = "EFECTIVO" },
    @{ nombre = "Carlos Ruiz"; dni = "35123456"; celular = ""; email = ""; planNombre = "LIBRE"; ultimoPeriodoPago = ""; montoUltimoPago = $null; metodoPago = "" },
    @{ nombre = "Plan Raro"; dni = "40111000"; celular = ""; email = ""; planNombre = "Plan Inexistente"; ultimoPeriodoPago = ""; montoUltimoPago = $null; metodoPago = "" }
) }
$i1 = Call POST "/api/alumnos/importar" $imp $tok
Write-Host "        Import #1: $($i1.Raw)"
Check "E6" "Importación de 3 filas -> 200" ($i1.Status -eq 200) "status=$($i1.Status)"
$i2 = Call POST "/api/alumnos/importar" $imp $tok
Write-Host "        Import #2 (re-import): $($i2.Raw)"
$todos = Call GET "/api/alumnos" $null $tok
$dnis = @($todos.Json | % { $_.dni })
Check "E6" "Re-importar NO duplica socios (DNI únicos)" (($dnis | Group-Object | ? Count -gt 1).Count -eq 0) "dnis=$($dnis -join ',')"
$ml = Call POST "/api/auth/login" @{ email = "28999888"; password = "28999888" }
Check "E6" "Socio importado entra con su DNI como clave" ($ml.Status -eq 200) "status=$($ml.Status) $($ml.Raw)"
Check "E6" "...y se le exige cambiar la clave" ($ml.Json.requiereCambioPassword -eq $true) "requiere=$($ml.Json.requiereCambioPassword)"
$maria = $todos.Json | ? { $_.dni -eq "28999888" }
Write-Host "        María estadoCuenta = $($maria.estadoCuenta)"

Write-Host "`n=== E4 · Rate-limit (al final, para no bloquear lo anterior) ==="
$codes = @()
for ($i = 0; $i -lt 8; $i++) { $codes += (Call POST "/api/auth/login" @{ email = $Admin2Email; password = "mala$i" }).Status }
Write-Host "        Intentos fallidos titular 2: $($codes -join ',')"
Check "E4" "Fuerza bruta contra titular 2 termina en 429" ($codes -contains 429) "codes=$($codes -join ',')"
$lb = Call POST "/api/auth/login" @{ email = $Admin1Email; password = $Admin1Pass }
Check "E4" "Titular 1 sigue pudiendo entrar (bloqueo es por usuario, no global)" ($lb.Status -eq 200) "status=$($lb.Status)"

Write-Host "`n=============================="
Write-Host " RESULTADO: $($script:ok) OK  /  $($script:fail) FALLAS"
Write-Host "=============================="
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
