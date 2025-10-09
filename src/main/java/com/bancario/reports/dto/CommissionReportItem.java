package com.bancario.reports.dto;

import com.bancario.reports.enums.ProductType;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Representa una línea agregada del reporte final de comisiones,
 * agrupada por tipo de producto.
 */
@Builder
public record CommissionReportItem(
        String productName,
        ProductType productType,
        BigDecimal totalFees // La suma de todas las comisiones
) {}