package com.bancario.reports.dto;

import com.bancario.reports.enums.ProductType;
import java.math.BigDecimal;

public record BalanceReportDTO(
        String accountId,
        ProductType productType,
        BigDecimal availableBalance
) { }