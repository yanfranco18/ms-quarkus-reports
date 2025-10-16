package com.bancario.reports.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.util.List;

/**
 * Data Transfer Object que consolida la información personal de un cliente
 * y la lista completa de productos (cuentas) que posee en el banco.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // No incluye campos nulos en la serialización JSON
public record ConsolidatedSummaryDTO(
        String customerId,
        String fullName,
        String customerStatus,
        // Lista de productos (cuentas, reutilizamos el DTO de AccountService)
        List<AccountResponse> products,
        String processingTimestamp // Marca de tiempo del procesamiento
) {}
