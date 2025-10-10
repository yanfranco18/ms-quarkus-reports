package com.bancario.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de salida que representa el resultado del cálculo del Saldo Promedio Diario (SPD)
 * de un cliente para un período específico.
 */
public record DailyAverageBalanceReportDto(
        String customerId,
        LocalDate startDate,
        LocalDate endDate,
        /**
         * El Saldo Promedio Diario consolidado de todos los productos (cuentas y créditos)
         * del cliente durante el período.
         */
        BigDecimal dailyAverageBalance
) {}