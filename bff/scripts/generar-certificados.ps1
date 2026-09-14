param([string]$Keytool = 'keytool')
$ErrorActionPreference = 'Stop'
$destino = Join-Path $PSScriptRoot '../.tls'
$configuracion = Join-Path $destino '.env.tls'
New-Item -ItemType Directory -Path $destino -Force | Out-Null
if (!(Test-Path $configuracion)) {
    $password = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    "TLS_KEYSTORE_PASSWORD=$password" | Set-Content -Encoding ascii $configuracion
}
$env:TLS_KEYSTORE_PASSWORD = (Get-Content $configuracion -Raw).Trim().Substring('TLS_KEYSTORE_PASSWORD='.Length)
$certificados = @()
foreach ($servicio in @('core','reports','bff-web','bff-movil','bff-cajero')) {
    $almacen = Join-Path $destino "$servicio.p12"
    $certificado = Join-Path $destino "$servicio.crt"
    if (!(Test-Path $almacen)) {
        & $Keytool -genkeypair -alias $servicio -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 90 -dname "CN=$servicio, OU=Desarrollo, O=Banco XYZ, C=CL" -ext "SAN=dns:$servicio,dns:localhost,ip:127.0.0.1" -ext EKU=serverAuth -keystore $almacen -storetype PKCS12 -storepass:env TLS_KEYSTORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw "No se pudo generar $servicio" }
    }
    & $Keytool -exportcert -rfc -alias $servicio -keystore $almacen -storepass:env TLS_KEYSTORE_PASSWORD -file $certificado
    if ($LASTEXITCODE -ne 0) { throw "No se pudo exportar $servicio" }
    $certificados += Get-Content $certificado -Raw
    $truststore = Join-Path $destino 'truststore.p12'
    & $Keytool -list -alias $servicio -keystore $truststore -storepass changeit 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        & $Keytool -importcert -noprompt -alias $servicio -file $certificado -keystore $truststore -storetype PKCS12 -storepass changeit
        if ($LASTEXITCODE -ne 0) { throw "No se pudo confiar en $servicio" }
    }
}
$certificados -join "`n" | Set-Content -Encoding ascii (Join-Path $destino 'certificados-publicos.pem')
Write-Output 'Certificados locales preparados. No suba la carpeta .tls a GitHub.'
