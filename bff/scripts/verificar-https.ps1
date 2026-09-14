param(
    [string]$Docker = 'C:\Users\Flekz13\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe',
    [string]$Curl = 'curl.exe'
)
$ErrorActionPreference = 'Stop'
$certificados = Join-Path $PSScriptRoot '../.tls/certificados-publicos.pem'
$config = (& $Docker inspect bank-batch-core-1 | ConvertFrom-Json)[0].Config.Env
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el servicio bancario.' }
$accountKey = (($config | Where-Object { $_.StartsWith('BANK_ATM_IDENTITIES=') }).Substring('BANK_ATM_IDENTITIES='.Length)).Split('=')[1]
$canales = @(
    @{Nombre='WEB';Puerto=8081;Ruta='/api/web/panel';Login='/api/web/login';Body='{"usuario":"web-demo","clave":"WebDemo2026!"}';Extra=@()},
    @{Nombre='MOBILE';Puerto=8082;Ruta='/api/movil/resumen';Login='/api/movil/login';Body='{"usuario":"mobile-demo","clave":"MobileDemo2026!"}';Extra=@()},
    @{Nombre='ATM';Puerto=8083;Ruta='/api/cajero/saldo';Login='/api/cajero/sesion';Body=(ConvertTo-Json @{cuenta=$accountKey;pin='1234'} -Compress);Extra=@('-H','X-ATM-Terminal: ATM-001','-H','X-ATM-Key: AtmTerminal2026!')}
)
$resultados = @()
$tokens = @{}
foreach ($canal in $canales) {
    $tokenJson = & $Curl --silent --show-error --fail --cacert $certificados -H 'Content-Type: application/json' @($canal.Extra) -d $canal.Body ('https://localhost:' + $canal.Puerto + $canal.Login)
    if ($LASTEXITCODE -ne 0) { throw "Fallo login HTTPS en $($canal.Nombre)" }
    $token = (($tokenJson -join "`n") | ConvertFrom-Json).accessToken
    $tokens[$canal.Nombre] = $token
    $url = 'https://localhost:' + $canal.Puerto + $canal.Ruta
    $respuesta = & $Curl --silent --show-error --fail --max-time 20 --cacert $certificados -H "Authorization: Bearer $token" @($canal.Extra) $url
    if ($LASTEXITCODE -ne 0) { throw "Fallo HTTPS autenticado en $($canal.Nombre)" }
    $datos = ($respuesta -join "`n") | ConvertFrom-Json
    $saldo = if ($canal.Nombre -eq 'WEB') { $datos.cuenta.saldoDisponible } else { $datos.saldoDisponible }
    if ($null -eq $saldo) { throw 'La respuesta no contiene saldo.' }
    $sinToken = & $Curl --silent --show-error --max-time 20 --cacert $certificados -o NUL -w '%{http_code}' @($canal.Extra) $url
    if ($LASTEXITCODE -ne 0 -or $sinToken -ne '401') { throw 'No se rechazo la consulta sin token.' }
    $http = & $Curl --silent --max-time 10 -o NUL -w '%{http_code}' ('http://localhost:' + $canal.Puerto + $canal.Ruta)
    if ($http -eq '200') { throw 'El servicio permite HTTP sin cifrado.' }
    $resultados += [pscustomobject]@{Canal=$canal.Nombre;Https='200';CertificadoValidado=$true;SinToken=$sinToken;HttpSinCifrar=$http;Saldo=$saldo}
}
if (@($resultados.Saldo | Select-Object -Unique).Count -ne 1) { throw 'Los canales no coinciden en el saldo.' }
$badPassword = & $Curl --silent --show-error --cacert $certificados -o NUL -w '%{http_code}' -H 'Content-Type: application/json' -d '{"usuario":"web-demo","clave":"incorrecta"}' https://localhost:8081/api/web/login
$mobileInWeb = & $Curl --silent --show-error --cacert $certificados -o NUL -w '%{http_code}' -H "Authorization: Bearer $($tokens.MOBILE)" https://localhost:8081/api/web/panel
$wrongTerminal = & $Curl --silent --show-error --cacert $certificados -o NUL -w '%{http_code}' -H "Authorization: Bearer $($tokens.ATM)" -H 'X-ATM-Terminal: ATM-002' -H 'X-ATM-Key: AtmTerminal2026!' https://localhost:8083/api/cajero/saldo
$seguridad = [pscustomobject]@{ClaveIncorrecta=$badPassword;TokenMovilEnWeb=$mobileInWeb;TerminalIncorrecto=$wrongTerminal}
if ($badPassword -ne '401' -or $mobileInWeb -ne '403' -or $wrongTerminal -ne '401') { throw 'Fallo una comprobacion de aislamiento o credenciales.' }
$destino = Join-Path $PSScriptRoot '../../output/seguridad-s5'
New-Item -ItemType Directory -Path $destino -Force | Out-Null
$resultados | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $destino 'https.json')
$seguridad | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $destino 'autorizacion.json')
$resultados | Format-Table -AutoSize
$seguridad | Format-Table -AutoSize
