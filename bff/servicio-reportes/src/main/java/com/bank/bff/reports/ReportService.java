package com.bank.bff.reports;

import com.bank.bff.api.Models.Movimiento;
import com.bank.bff.api.Models.MovimientoBreve;
import com.bank.bff.api.Models.PaginaMovimientos;
import com.bank.bff.api.Models.ResumenAnual;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PaginaMovimientos movimientos(
            String accountKey, Long cuentaId, int pagina, int tamanio) {
        validate(cuentaId, pagina, tamanio);
        authorizeAccount(accountKey, cuentaId);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM annual_statement WHERE cuenta_id = ?",
                Long.class,
                cuentaId);
        List<Movimiento> content = jdbc.query("""
                SELECT record_key, cuenta_id, fecha, transaccion, monto, descripcion, is_anomaly
                FROM annual_statement
                WHERE cuenta_id = ?
                ORDER BY fecha DESC, record_key
                LIMIT ? OFFSET ?
                """,
                (rs, row) -> new Movimiento(
                        rs.getString("record_key"),
                        rs.getLong("cuenta_id"),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getString("transaccion"),
                        rs.getBigDecimal("monto"),
                        rs.getString("descripcion"),
                        rs.getBoolean("is_anomaly")),
                cuentaId,
                tamanio,
                pagina * tamanio);
        return new PaginaMovimientos(content, pagina, tamanio, total);
    }

    @Transactional(readOnly = true)
    public List<MovimientoBreve> movimientosBreves(
            String accountKey, Long cuentaId, int limite) {
        if (cuentaId == null || cuentaId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta invalida");
        }
        if (limite < 1 || limite > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El limite debe estar entre 1 y 10");
        }
        authorizeAccount(accountKey, cuentaId);
        return jdbc.query("""
                SELECT fecha, transaccion, monto
                FROM annual_statement
                WHERE cuenta_id = ?
                ORDER BY fecha DESC, record_key
                LIMIT ?
                """,
                (rs, row) -> new MovimientoBreve(
                        rs.getDate("fecha").toLocalDate(),
                        rs.getString("transaccion"),
                        rs.getBigDecimal("monto")),
                cuentaId,
                limite);
    }

    @Transactional(readOnly = true)
    public List<ResumenAnual> resumenes(String accountKey, Long cuentaId) {
        if (cuentaId == null || cuentaId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta invalida");
        }
        authorizeAccount(accountKey, cuentaId);
        return jdbc.query("""
                SELECT cuenta_id, report_year, transaction_count, anomaly_count,
                       total_deposits, total_withdrawals, net_balance
                FROM annual_account_summary
                WHERE cuenta_id = ?
                ORDER BY report_year DESC
                """,
                (rs, row) -> new ResumenAnual(
                        rs.getLong("cuenta_id"),
                        rs.getInt("report_year"),
                        rs.getLong("transaction_count"),
                        rs.getLong("anomaly_count"),
                        rs.getBigDecimal("total_deposits"),
                        rs.getBigDecimal("total_withdrawals"),
                        rs.getBigDecimal("net_balance")),
                cuentaId);
    }

    private void validate(Long cuentaId, int pagina, int tamanio) {
        if (cuentaId == null || cuentaId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta invalida");
        }
        if (pagina < 0 || tamanio < 1 || tamanio > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La pagina debe ser positiva y el tamanio debe estar entre 1 y 50");
        }
    }

    private void authorizeAccount(String accountKey, Long cuentaId) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM processed_account
                WHERE record_key = ? AND cuenta_id = ?
                """, Integer.class, accountKey, cuentaId);
        if (matches == null || matches == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "La cuenta no pertenece al usuario autenticado");
        }
    }
}
