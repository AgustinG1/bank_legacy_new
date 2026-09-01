# Banco XYZ - Procesamiento Batch Financiero (Particionado)

Aplicación ETL desarrollada con Java 17 y Spring Batch para modernizar tres procesos legacy del Banco XYZ: transacciones diarias, cálculo de intereses mensuales y estados de cuenta anuales.

El proyecto procesa los archivos CSV de las semanas 1, 2 y 3 mediante particiones concurrentes, validaciones de datos, persistencia en PostgreSQL y políticas de tolerancia a fallos.

## 🚀 Tecnologías utilizadas

- **Java 17**
- **Spring Boot 3.2.5**
- **Spring Batch 5:** Jobs, Steps, procesamiento por chunks y particiones.
- **Spring Data JPA:** persistencia de los resultados.
- **PostgreSQL 15:** base de datos relacional.
- **Docker Compose:** ejecución reproducible de PostgreSQL.
- **Lombok:** generación de código repetitivo.
- **Maven y JUnit 5:** construcción y pruebas automatizadas.

## ⚙️ Arquitectura de procesamiento paralelo

Se implementó **particionamiento local por archivo**. `MultiResourcePartitioner` crea una partición para cada semana y `ThreadPoolTaskExecutor` permite ejecutar hasta tres archivos simultáneamente.

Cada proceso sigue el flujo:

```text
CSV -> ItemReader -> ItemProcessor -> ItemWriter JPA -> PostgreSQL
```

La configuración predeterminada utiliza 3 hilos, 3 particiones y chunks de 50 registros. Estos valores pueden modificarse para comparar el rendimiento con 1, 2 y 3 hilos.

### Flujo 1: Transacciones diarias (`dailyTransactionJob`)

- **Reader:** lee los tres archivos `transacciones.csv` y admite múltiples formatos de fecha.
- **Processor:** normaliza tipos, detecta montos incorrectos, marca anomalías y elimina duplicados exactos.
- **Writer:** persiste el detalle en `processed_transaction`.
- **Reporte:** genera `daily_transaction_summary` y `output/daily_transaction_summary.csv`.

### Flujo 2: Intereses mensuales (`monthlyInterestJob`)

- **Reader:** lee los tres archivos `intereses.csv`.
- **Processor:** valida nombre, edad, saldo y tipo de cuenta; aplica un interés del 5 % a cuentas de ahorro y préstamo.
- **Writer:** almacena saldo, tasa, interés calculado y saldo final en `processed_account`.

### Flujo 3: Estados de cuenta anuales (`annualStatementJob`)

- **Reader:** lee los tres archivos `cuentas_anuales.csv` y convierte fechas inconsistentes.
- **Processor:** normaliza movimientos y descripciones, controla montos y registra anomalías.
- **Writer:** guarda el detalle en `annual_statement`.
- **Reporte:** genera `annual_account_summary` y `output/annual_account_summary.csv` para auditoría.

## 🛡️ Resiliencia y tolerancia a fallos

El sistema continúa procesando aunque encuentre datos incorrectos:

1. **Validación y limpieza:** fechas, valores monetarios, tipos, edades, descripciones y duplicados son controlados antes de escribir.
2. **Política de omisión:** `CustomSkipPolicy` permite omitir únicamente errores de validación, formato CSV o integridad de datos, con un límite configurable de 2000.
3. **Trazabilidad:** `CustomSkipListener` registra la fase, el elemento omitido y su causa.
4. **Reintentos:** los errores transitorios de PostgreSQL tienen hasta 3 intentos con espera exponencial.
5. **Resumen de ejecución:** cada Job informa lecturas, escrituras, filtros, omisiones, commits, rollbacks y duración.

## 🗄️ Modelo de datos (PostgreSQL)

El sistema genera las siguientes tablas de negocio:

1. `processed_transaction`: transacciones procesadas y anomalías.
2. `daily_transaction_summary`: resumen diario de créditos y débitos.
3. `processed_account`: cuentas con intereses y saldos finales.
4. `annual_statement`: movimientos anuales detallados.
5. `annual_account_summary`: informe anual agrupado por cuenta.

Spring Batch también utiliza sus tablas `BATCH_JOB_*` y `BATCH_STEP_*` para conservar el historial de ejecución.

## 🛠️ Configuración y ejecución

### 1. Iniciar PostgreSQL

Con Docker Desktop abierto:

```powershell
docker compose up -d
docker compose ps
```

Configuración local:

```text
Base de datos: bankdb
Usuario: bankuser
Contraseña: bankpassword
Puerto: 5432
```

### 2. Archivos de entrada

Los nueve archivos se encuentran en:

```text
data/semana_1/
data/semana_2/
data/semana_3/
```

Cada carpeta contiene `transacciones.csv`, `intereses.csv` y `cuentas_anuales.csv`.

### 3. Compilar y ejecutar pruebas

```powershell
mvn clean verify
```

El proyecto contiene 11 pruebas automatizadas. El resultado esperado es `BUILD SUCCESS`.

### 4. Ejecutar los Jobs

```powershell
java -jar target\bank-batch-0.0.1-SNAPSHOT.jar --spring.batch.job.name=dailyTransactionJob

java -jar target\bank-batch-0.0.1-SNAPSHOT.jar --spring.batch.job.name=monthlyInterestJob

java -jar target\bank-batch-0.0.1-SNAPSHOT.jar --spring.batch.job.name=annualStatementJob
```

Cada ejecución correcta termina con `JOB_RESUMEN ... estado=COMPLETED`.
