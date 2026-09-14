CREATE TABLE IF NOT EXISTS bff_saldo (
    record_key VARCHAR(36) PRIMARY KEY,
    saldo_disponible NUMERIC(19,2) NOT NULL CHECK (saldo_disponible >= 0)
);

CREATE TABLE IF NOT EXISTS bff_retiro (
    solicitud_id VARCHAR(36) PRIMARY KEY,
    record_key VARCHAR(36) NOT NULL REFERENCES bff_saldo(record_key),
    monto NUMERIC(19,2) NOT NULL CHECK (monto > 0),
    saldo_restante NUMERIC(19,2) NOT NULL,
    fecha TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bff_retiro_cuenta_fecha
    ON bff_retiro (record_key, fecha DESC);
