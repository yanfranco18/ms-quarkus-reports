package com.bancario.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para la transferencia de datos históricos de saldos/estados al final del día (EOD).
 * El contrato de datos debe coincidir exactamente con la entidad de respuesta del account-service.
 */
public record DailyBalanceHistoryDto(
        String productId,
        String accountType,
        String productType, // CLAVE: "PASSIVE" (Depósito) o "ACTIVE" (Crédito)
        LocalDate date,
        BigDecimal balanceEOD,
        BigDecimal amountUsedEOD
) {}