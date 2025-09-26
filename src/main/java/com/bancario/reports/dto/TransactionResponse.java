package com.bancario.reports.dto;

import com.bancario.reports.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO que representa un movimiento o transaccion.
 * Se utiliza para mapear la respuesta del transactions-service.
 */
public record TransactionResponse(
        String id,
        String accountId,
        String customerId,
        TransactionType transactionType,
        BigDecimal amount,
        LocalDateTime transactionDate,
        String description
) {}