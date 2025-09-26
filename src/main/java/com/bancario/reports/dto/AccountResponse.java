package com.bancario.reports.dto;

import com.bancario.reports.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccountResponse(
        String id,
        String customerId,
        String accountNumber,
        ProductType productType,
        AccountType accountType,
        CreditType creditType,
        BigDecimal balance,
        BigDecimal amountUsed,
        LocalDateTime openingDate,
        Integer monthlyMovements,
        LocalDateTime specificDepositDate,
        AccountStatus status,
        List<String> holders,
        List<String> signatories
) {}
