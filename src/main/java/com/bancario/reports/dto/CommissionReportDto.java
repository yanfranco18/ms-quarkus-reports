package com.bancario.reports.dto;

import com.bancario.reports.enums.ProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO utilizado para transferir datos detallados de una comisión
 * (uno por transacción) desde el Transaction-Service al Report-Service.
 */
public record CommissionReportDto(
        String accountId,
        ProductType productType,
        String productName, // Nombre específico (SAVINGS_ACCOUNT, CREDIT_CARD)
        BigDecimal fee,
        LocalDateTime transactionDate
) {}