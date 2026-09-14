package com.bank.bff.core;

import com.bank.bff.api.Models.Cuenta;
import com.bank.bff.api.Models.Retiro;
import com.bank.bff.api.Models.Resumen;
import com.bank.bff.api.Models.Saldo;
import com.bank.bff.api.Models.SolicitudRetiro;
import com.bank.bff.security.IdentityRegistry;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankService {
    private final JdbcTemplate jdbc;
    private final IdentityRegistry identities;

    public BankService(JdbcTemplate jdbc, IdentityRegistry identities) {
        this.jdbc = jdbc;
        this.identities = identities;
    }

    @Transactional(readOnly = true)
    public Cuenta detalle(String accountKey) {
        return loadAccount(accountKey, false);
    }

    @Transactional(readOnly = true)
    public Resumen resumen(String accountKey) {
        var results = jdbc.query("""
                SELECT a.cuenta_id, a.tipo, b.saldo_disponible
                FROM processed_account a
                JOIN bff_saldo b ON a.record_key = b.record_key
                WHERE a.record_key = ?
                """,
                (rs, row) -> new Resumen(
                        rs.getLong("cuenta_id"),
                        rs.getString("tipo"),
                        rs.getBigDecimal("saldo_disponible")),
                accountKey);
        if (results.isEmpty()) {
            throw accountNotFound();
        }
        return results.get(0);
    }

    @Transactional(readOnly = true)
    public Saldo saldo(String accountKey) {
        var results = jdbc.query("""
                SELECT a.cuenta_id, b.saldo_disponible
                FROM processed_account a
                JOIN bff_saldo b ON a.record_key = b.record_key
                WHERE a.record_key = ?
                """,
                (rs, row) -> new Saldo(
                        rs.getLong("cuenta_id"),
                        rs.getBigDecimal("saldo_disponible")),
                accountKey);
        if (results.isEmpty()) {
            throw accountNotFound();
        }
        return results.get(0);
    }

    @Transactional(readOnly = true)
    public List<Retiro> retiros(String accountKey) {
        return jdbc.query("""
                SELECT solicitud_id, monto, saldo_restante
                FROM bff_retiro
                WHERE record_key = ?
                ORDER BY fecha DESC, solicitud_id
                LIMIT 50
                """,
                (rs, row) -> new Retiro(
                        UUID.fromString(rs.getString(1)),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3)),
                accountKey);
    }

    @Transactional
    public Retiro retirar(String accountKey, SolicitudRetiro request) {
        if (request == null || request.solicitudId() == null || request.monto() == null
                || request.monto().signum() <= 0 || request.monto().scale() > 2
                || request.monto().compareTo(new BigDecimal("200000.00")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto o solicitud invalida");
        }

        Cuenta account = loadAccount(accountKey, true);
        var previous = jdbc.query("""
                SELECT solicitud_id, monto, saldo_restante
                FROM bff_retiro
                WHERE solicitud_id = ? AND record_key = ?
                """,
                (rs, row) -> new Retiro(
                        UUID.fromString(rs.getString(1)),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3)),
                request.solicitudId().toString(), accountKey);

        if (!previous.isEmpty()) {
            if (previous.get(0).monto().compareTo(request.monto()) != 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "La solicitud ya existe con otro monto");
            }
            return previous.get(0);
        }
        if (!"ahorro".equals(account.tipo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Solo se permiten retiros de cuentas de ahorro");
        }
        if (account.saldoDisponible().compareTo(request.monto()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        }

        BigDecimal remaining = account.saldoDisponible().subtract(request.monto());
        jdbc.update("UPDATE bff_saldo SET saldo_disponible = ? WHERE record_key = ?",
                remaining, accountKey);
        jdbc.update("""
                INSERT INTO bff_retiro (solicitud_id, record_key, monto, saldo_restante)
                VALUES (?, ?, ?, ?)
                """,
                request.solicitudId().toString(), accountKey, request.monto(), remaining);
        return new Retiro(request.solicitudId(), request.monto().setScale(2), remaining);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeBalance() {
        identities.accountKeys().forEach(accountKey -> jdbc.update("""
                    INSERT INTO bff_saldo (record_key, saldo_disponible)
                    SELECT record_key, saldo_final
                    FROM processed_account
                    WHERE record_key = ?
                    ON CONFLICT DO NOTHING
                    """, accountKey));
    }

    private Cuenta loadAccount(String accountKey, boolean lock) {
        var results = jdbc.query("""
                SELECT a.record_key, a.cuenta_id, a.nombre, a.edad, a.tipo, a.saldo,
                       a.tasa_aplicada, a.interes_calculado, a.saldo_final, b.saldo_disponible
                FROM processed_account a
                JOIN bff_saldo b ON a.record_key = b.record_key
                WHERE a.record_key = ?
                """ + (lock ? " FOR UPDATE" : ""), this::mapAccount, accountKey);
        if (results.isEmpty()) {
            throw accountNotFound();
        }
        return results.get(0);
    }

    private Cuenta mapAccount(ResultSet rs, int row) throws SQLException {
        return new Cuenta(
                rs.getString(1), rs.getLong(2), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10));
    }

    private ResponseStatusException accountNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cuenta no encontrada; ejecute primero el proceso batch de intereses");
    }
}
