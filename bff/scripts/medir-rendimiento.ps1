param(
    [int]$Solicitudes = 100,
    [int]$Concurrencia = 5,
    [int]$Rondas = 3,
    [string]$Docker = 'C:\Users\Flekz13\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
)
$ErrorActionPreference = 'Stop'
if ($Solicitudes -lt 1 -or $Concurrencia -lt 1 -or $Rondas -lt 1) { throw 'Los parametros deben ser positivos.' }
Add-Type -TypeDefinition @'
using System;
using System.Linq;
using System.Net.Http;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;
public class MuestraS5 {
 public double Ms {get;set;}
 public int Estado {get;set;}
 public int Bytes {get;set;}
 public string Codificacion {get;set;}
}
public class CargaS5 {
 public static async Task<MuestraS5[]> Ejecutar(string url,string token,string encoding,int total,int concurrencia) {
  using(var handler = new HttpClientHandler { AutomaticDecompression = System.Net.DecompressionMethods.None })
  using(var client = new HttpClient(handler))
  using(var sem = new SemaphoreSlim(concurrencia)) {
   client.Timeout = TimeSpan.FromSeconds(20);
   client.DefaultRequestHeaders.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer",token);
   client.DefaultRequestHeaders.AcceptEncoding.ParseAdd(encoding);
   for(int i=0;i<10;i++) { using(var warm = await client.GetAsync(url)) { await warm.Content.ReadAsByteArrayAsync(); warm.EnsureSuccessStatusCode(); } }
   var tasks = Enumerable.Range(0,total).Select(async i => {
    await sem.WaitAsync(); var watch = Stopwatch.StartNew();
    try { using(var response = await client.GetAsync(url)) {
      var body = await response.Content.ReadAsByteArrayAsync(); watch.Stop();
      return new MuestraS5 {Ms=watch.Elapsed.TotalMilliseconds,Estado=(int)response.StatusCode,Bytes=body.Length,Codificacion=string.Join(",",response.Content.Headers.ContentEncoding)};
    }} catch(HttpRequestException) { return new MuestraS5 {Ms=watch.Elapsed.TotalMilliseconds,Estado=0}; }
    catch(TaskCanceledException) { return new MuestraS5 {Ms=watch.Elapsed.TotalMilliseconds,Estado=0}; }
    finally {sem.Release();}
   }).ToArray();
   return await Task.WhenAll(tasks);
  }
 }
}
'@
$config = (& $Docker inspect bank-batch-core-1 | ConvertFrom-Json)[0].Config.Env
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar Docker.' }
$canales = @(
    @{Nombre='web'; Identidad='WEB'; Url='http://127.0.0.1:8081/api/web/panel?pagina=0&tamanio=10'},
    @{Nombre='movil'; Identidad='MOBILE'; Url='http://127.0.0.1:8082/api/movil/resumen'},
    @{Nombre='cajero'; Identidad='ATM'; Url='http://127.0.0.1:8083/api/cajero/saldo'}
)
$destino = Join-Path $PSScriptRoot ('../../output/rendimiento-s5/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $destino -Force | Out-Null
$monitor = Start-Job -ArgumentList $Docker -ScriptBlock {
    param($dockerPath)
    while ($true) {
        $fecha = [DateTime]::UtcNow.ToString('o')
        & $dockerPath stats --no-stream bank-batch-db-1 bank-batch-core-1 bank-batch-reports-1 bank-batch-bff-web-1 bank-batch-bff-movil-1 bank-batch-bff-cajero-1 --format '{{json .}}' | ForEach-Object {
            [pscustomobject]@{FechaUtc=$fecha; Datos=($_ | ConvertFrom-Json)}
        }
    }
}
$resultados = @()
try {
    for ($ronda=1; $ronda -le $Rondas; $ronda++) {
        $orden = if ($ronda % 2 -eq 1) { @('identity','gzip') } else { @('gzip','identity') }
        foreach ($encoding in $orden) {
            foreach ($canal in $canales) {
                $prefijo = 'BANK_' + $canal.Identidad + '_IDENTITIES='
                $token = (($config | Where-Object { $_.StartsWith($prefijo) }).Substring($prefijo.Length)).Split('=')[0]
                $muestras = [CargaS5]::Ejecutar($canal.Url,$token,$encoding,$Solicitudes,$Concurrencia).GetAwaiter().GetResult()
                $ordenadas = @($muestras.Ms | Sort-Object)
                $resumen = [pscustomobject]@{
                    Canal=$canal.Nombre; CodificacionSolicitada=$encoding; Ronda=$ronda
                    Solicitudes=$Solicitudes; Concurrencia=$Concurrencia
                    Errores=@($muestras | Where-Object Estado -ne 200).Count
                    MediaMs=[Math]::Round(($muestras.Ms | Measure-Object -Average).Average,2)
                    P95Ms=[Math]::Round($ordenadas[[Math]::Ceiling(0.95*$ordenadas.Count)-1],2)
                    BytesCuerpoMedios=[Math]::Round(($muestras.Bytes | Measure-Object -Average).Average,2)
                    RespuestasGzip=@($muestras | Where-Object Codificacion -eq 'gzip').Count
                }
                $resultados += $resumen
                $muestras | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $destino "$($canal.Nombre)-$encoding-$ronda.json")
                $resumen | Format-Table -AutoSize | Out-Host
            }
        }
    }
} finally {
    Stop-Job $monitor
    $recursos = @(Receive-Job $monitor)
    Remove-Job $monitor
    $recursos | Select-Object FechaUtc,Datos | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $destino 'recursos.json')
    $resultados | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $destino 'resumen.json')
}
Write-Output "Resultados: $destino"
if (@($resultados | Where-Object Errores -gt 0).Count -gt 0) { throw 'Hubo solicitudes fallidas; revisar resultados.' }
