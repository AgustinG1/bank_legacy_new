package com.bank.bff;

import com.bank.bff.atm.AtmBffApplication;
import com.bank.bff.core.BankingServiceApplication;
import com.bank.bff.mobile.MobileBffApplication;
import com.bank.bff.reports.ReportsServiceApplication;
import com.bank.bff.web.WebBffApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BffIntegrationTest {
    private static final boolean POSTGRES = "true".equals(System.getenv("BFF_TEST_POSTGRES"));
    private static final String SCHEMA = "bff_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String BASE_DB = "jdbc:postgresql://localhost:5432/bankdb";
    private static final String DB = POSTGRES ? BASE_DB + "?currentSchema=" + SCHEMA
            : "jdbc:h2:mem:bff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    private static final String USER = POSTGRES ? "bankuser" : "sa";
    private static final String PASSWORD = POSTGRES ? "bankpassword" : "";
    private static final String WEB = "web-test-012345678901234567890123456";
    private static final String WEB_OTHER = "web-other-0123456789012345678901234";
    private static final String MOBILE = "mobile-test-01234567890123456789012";
    private static final String ATM = "atm-test-012345678901234567890123456";
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate jdbc;
    private String core;
    private String reports;
    private String web;
    private String mobile;
    private String atm;

    @BeforeAll
    void startServices() throws Exception {
        if (POSTGRES) {
            try (var connection = DriverManager.getConnection(BASE_DB, USER, PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + SCHEMA);
            }
        }
        try (var connection = DriverManager.getConnection(DB, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE processed_account (
                    record_key VARCHAR(36) PRIMARY KEY, cuenta_id BIGINT, nombre VARCHAR(100),
                    edad INTEGER, tipo VARCHAR(20), saldo NUMERIC(19,2), tasa_aplicada NUMERIC(7,4),
                    interes_calculado NUMERIC(19,2), saldo_final NUMERIC(19,2));
                """);
            statement.execute("INSERT INTO processed_account VALUES ('cuenta-prueba', 101, 'John Doe', 30, 'ahorro', 5000, 0.05, 250, 5250)");
            statement.execute("INSERT INTO processed_account VALUES ('cuenta-otra', 202, 'Jane Doe', 41, 'ahorro', 8000, 0.05, 400, 8400)");
            statement.execute("""
                CREATE TABLE annual_statement (
                    record_key VARCHAR(36) PRIMARY KEY, cuenta_id BIGINT, fecha DATE,
                    transaccion VARCHAR(30), monto NUMERIC(19,2), descripcion VARCHAR(255),
                    is_anomaly BOOLEAN);
                """);
            statement.execute("INSERT INTO annual_statement VALUES ('mov-1', 101, DATE '2024-12-10', 'deposito', 1000, 'Ingreso mensual', FALSE)");
            statement.execute("INSERT INTO annual_statement VALUES ('mov-2', 101, DATE '2024-11-05', 'retiro', 200, 'Retiro cajero', FALSE)");
            statement.execute("INSERT INTO annual_statement VALUES ('mov-otra', 202, DATE '2024-10-01', 'deposito', 400, 'Abono', FALSE)");
            statement.execute("""
                CREATE TABLE annual_account_summary (
                    id VARCHAR(80) PRIMARY KEY, cuenta_id BIGINT, report_year INTEGER,
                    transaction_count BIGINT, anomaly_count BIGINT,
                    total_deposits NUMERIC(19,2), total_withdrawals NUMERIC(19,2),
                    net_balance NUMERIC(19,2));
                """);
            statement.execute("INSERT INTO annual_account_summary VALUES ('101-2024', 101, 2024, 2, 0, 1000, 200, 800)");
            statement.execute("INSERT INTO annual_account_summary VALUES ('202-2024', 202, 2024, 1, 0, 400, 0, 400)");
        }
        jdbc = new JdbcTemplate(new DriverManagerDataSource(DB, USER, PASSWORD));
        core = start(BankingServiceApplication.class, "core", null, null);
        reports = start(ReportsServiceApplication.class, "reports", null, null);
        web = start(WebBffApplication.class, "web", core, reports);
        mobile = start(MobileBffApplication.class, "mobile", core, reports);
        atm = start(AtmBffApplication.class, "atm", core, null);
    }

    private String start(
            Class<?> application, String channel, String coreUpstream, String reportsUpstream) {
        var args = new ArrayList<>(List.of(
            "--server.port=0", "--bank.channel=" + channel,
            "--bank.web-identities=" + WEB + "=cuenta-prueba," + WEB_OTHER + "=cuenta-otra",
            "--bank.mobile-identities=" + MOBILE + "=cuenta-prueba",
            "--bank.atm-identities=" + ATM + "=cuenta-prueba",
            "--spring.datasource.url=" + DB,
            "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD,
            "--spring.main.banner-mode=off", "--logging.level.root=WARN",
            "--bank.security.allow-legacy-tokens=true"));
        if (channel.equals("core")) {
            args.add("--spring.sql.init.mode=always");
            args.add("--spring.sql.init.schema-locations=classpath:schema-core.sql");
        } else if (channel.equals("reports")) {
            args.add("--spring.sql.init.mode=never");
        } else {
            args.add("--bank.core-url=" + coreUpstream);
            if (reportsUpstream != null) {
                args.add("--bank.reports-url=" + reportsUpstream);
            }
            args.add("--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        }
        var context = new SpringApplicationBuilder(application).run(args.toArray(String[]::new));
        contexts.add(context);
        return "http://127.0.0.1:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    void stopServices() throws Exception {
        Collections.reverse(contexts);
        contexts.forEach(ConfigurableApplicationContext::close);
        if (POSTGRES) {
            try (var connection = DriverManager.getConnection(BASE_DB, USER, PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @BeforeEach
    void resetAccount() {
        jdbc.update("DELETE FROM bff_retiro");
        jdbc.update("DELETE FROM bff_saldo");
        jdbc.update("UPDATE processed_account SET tipo='ahorro', saldo_final=5250 WHERE record_key='cuenta-prueba'");
        jdbc.update("INSERT INTO bff_saldo (record_key, saldo_disponible) VALUES ('cuenta-prueba', 5250)");
        jdbc.update("INSERT INTO bff_saldo (record_key, saldo_disponible) VALUES ('cuenta-otra', 8400)");
    }

    private HttpResponse<String> request(String url, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String withdrawal(UUID id, String amount) {
        return "{\"solicitudId\":\"" + id + "\",\"monto\":" + amount + "}";
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT saldo_disponible FROM bff_saldo WHERE record_key='cuenta-prueba'", BigDecimal.class);
    }

    @Test
    void webEntregaDetalleYMovilSoloCamposEsenciales() throws Exception {
        var full = request(web + "/api/web/panel", WEB, null);
        var light = request(mobile + "/api/movil/resumen", MOBILE, null);
        assertEquals(200, full.statusCode());
        assertEquals(200, light.statusCode());
        JsonNode fullData = json.readTree(full.body());
        JsonNode lightData = json.readTree(light.body());
        assertEquals("John Doe", fullData.at("/cuenta/nombre").asText());
        assertTrue(fullData.at("/cuenta/tasaAplicada").isNumber());
        assertTrue(fullData.get("retirosRecientes").isArray());
        assertEquals(4, lightData.size());
        assertFalse(lightData.has("edad"));
        assertFalse(lightData.has("nombre"));
        assertEquals(2, lightData.get("ultimosMovimientos").size());
        assertEquals(3, lightData.at("/ultimosMovimientos/0").size());
        assertFalse(lightData.at("/ultimosMovimientos/0").has("descripcion"));
        assertTrue(light.body().length() < full.body().length());
    }

    @Test
    void webAgregaCuentaMovimientosYResumenDesdeDosServicios() throws Exception {
        var result = request(web + "/api/web/panel?pagina=0&tamanio=1", WEB, null);
        assertEquals(200, result.statusCode());
        JsonNode panel = json.readTree(result.body());
        assertEquals(101, panel.at("/cuenta/cuentaId").asInt());
        assertEquals(2, panel.at("/movimientos/totalElementos").asInt());
        assertEquals(1, panel.at("/movimientos/contenido").size());
        assertEquals("mov-1", panel.at("/movimientos/contenido/0/clave").asText());
        assertEquals(2024, panel.at("/resumenesAnuales/0/anio").asInt());
    }

    @Test
    void cadaIdentidadSoloAccedeALaCuentaQueTieneAsociada() throws Exception {
        var other = request(web + "/api/web/panel", WEB_OTHER, null);
        assertEquals(200, other.statusCode());
        JsonNode panel = json.readTree(other.body());
        assertEquals(202, panel.at("/cuenta/cuentaId").asInt());
        assertEquals("Jane Doe", panel.at("/cuenta/nombre").asText());
        assertEquals("mov-otra", panel.at("/movimientos/contenido/0/clave").asText());

        var forbidden = request(
                reports + "/interno/reportes/cuentas/202/movimientos", WEB, null);
        assertEquals(403, forbidden.statusCode());
    }

    @Test
    void cajeroSoloEntregaCuentaYSaldo() throws Exception {
        var result = request(atm + "/api/cajero/saldo", ATM, null);
        assertEquals(200, result.statusCode());
        assertEquals(2, json.readTree(result.body()).size());
        assertEquals(5250, json.readTree(result.body()).get("saldoDisponible").asInt());
    }

    @Test
    void sinTokenOConTokenInvalidoRetorna401() throws Exception {
        assertEquals(401, request(web + "/api/web/panel", null, null).statusCode());
        assertEquals(401, request(atm + "/api/cajero/saldo", "invalido", null).statusCode());
    }

    @Test
    void tokenDeOtroCanalYRetiroDirectoDesdeWebRetornan403() throws Exception {
        assertEquals(403, request(atm + "/api/cajero/saldo", WEB, null).statusCode());
        assertEquals(403, request(web + "/api/web/panel", MOBILE, null).statusCode());
        assertEquals(403, request(core + "/interno/retiros", WEB, withdrawal(UUID.randomUUID(), "100")).statusCode());
        assertEquals(403, request(reports + "/interno/reportes/cuentas/101/movimientos", ATM, null).statusCode());
        assertEquals(403, request(reports + "/interno/reportes/cuentas/101/resumenes-anuales", MOBILE, null).statusCode());
        assertEquals(403, request(core + "/interno/cuentas/detalle", MOBILE, null).statusCode());
        assertEquals(403, request(core + "/interno/cuentas/resumen", ATM, null).statusCode());
    }

    @Test
    void noSeExponenRutasDeOtrosCanales() throws Exception {
        assertEquals(403, request(web + "/api/cajero/saldo", WEB, null).statusCode());
        assertEquals(403, request(mobile + "/interno/cuenta", MOBILE, null).statusCode());
    }

    @Test
    void retiroActualizaSaldoEnTodosLosCanalesSinModificarBatch() throws Exception {
        var result = request(atm + "/api/cajero/retiros", ATM, withdrawal(UUID.randomUUID(), "250.00"));
        assertEquals(200, result.statusCode());
        assertEquals("APROBADO", json.readTree(result.body()).get("estado").asText());
        assertEquals(5000, json.readTree(result.body()).get("saldoDisponible").asInt());
        assertEquals(0, balance().compareTo(new BigDecimal("5000")));
        var light = request(mobile + "/api/movil/resumen", MOBILE, null);
        assertEquals(5000, json.readTree(light.body()).get("saldoDisponible").asInt());
        var full = request(web + "/api/web/panel", WEB, null);
        assertEquals(1, json.readTree(full.body()).get("retirosRecientes").size());
        assertEquals(5250, jdbc.queryForObject(
                "SELECT saldo_final FROM processed_account WHERE record_key='cuenta-prueba'",
                Integer.class));
    }

    @Test
    void solicitudRepetidaNoDuplicaCobro() throws Exception {
        String body = withdrawal(UUID.randomUUID(), "100");
        var first = request(atm + "/api/cajero/retiros", ATM, body);
        var second = request(atm + "/api/cajero/retiros", ATM, body);
        assertEquals(200, first.statusCode());
        assertEquals(first.body(), second.body());
        assertEquals(0, balance().compareTo(new BigDecimal("5150")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bff_retiro", Integer.class));
    }

    @Test
    void solicitudRepetidaConOtroMontoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(200, request(atm + "/api/cajero/retiros", ATM, withdrawal(id, "100")).statusCode());
        assertEquals(409, request(atm + "/api/cajero/retiros", ATM, withdrawal(id, "200")).statusCode());
        assertEquals(0, balance().compareTo(new BigDecimal("5150")));
    }

    @Test
    void rechazaMontosInvalidos() throws Exception {
        for (String amount : List.of("0", "-1", "0.001", "200001", "null")) {
            assertEquals(400, request(atm + "/api/cajero/retiros", ATM, withdrawal(UUID.randomUUID(), amount)).statusCode(), amount);
        }
        assertEquals(400, request(atm + "/api/cajero/retiros", ATM, "{\"monto\":100}").statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bff_retiro", Integer.class));
    }

    @Test
    void saldoInsuficienteNoGeneraRetiro() throws Exception {
        assertEquals(200, request(atm + "/api/cajero/saldo", ATM, null).statusCode());
        assertEquals(409, request(atm + "/api/cajero/retiros", ATM, withdrawal(UUID.randomUUID(), "6000")).statusCode());
        assertEquals(5250, balance().intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bff_retiro", Integer.class));
    }

    @Test
    void noPermiteRetirarDePrestamo() throws Exception {
        jdbc.update("UPDATE processed_account SET tipo='prestamo'");
        assertEquals(409, request(atm + "/api/cajero/retiros", ATM, withdrawal(UUID.randomUUID(), "100")).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bff_retiro", Integer.class));
    }

    @Test
    void retirosConcurrentesNoSobregiran() throws Exception {
        assertEquals(200, request(atm + "/api/cajero/saldo", ATM, null).statusCode());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<Integer> task = () -> {
                gate.await();
                return request(atm + "/api/cajero/retiros", ATM, withdrawal(UUID.randomUUID(), "4000")).statusCode();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            gate.countDown();
            var statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
            assertEquals(1250, balance().intValue());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bff_retiro", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void falloAlGuardarAuditoriaRevierteDescuento() throws Exception {
        assertEquals(200, request(atm + "/api/cajero/saldo", ATM, null).statusCode());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bff_saldo VALUES ('otra-cuenta', 1000)");
        jdbc.update("INSERT INTO bff_retiro (solicitud_id, record_key, monto, saldo_restante) VALUES (?, 'otra-cuenta', 10, 990)", id.toString());
        assertEquals(502, request(atm + "/api/cajero/retiros", ATM, withdrawal(id, "100")).statusCode());
        assertEquals(5250, balance().intValue());
    }
}
