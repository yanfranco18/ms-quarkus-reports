package com.bancario.reports.service;

import com.bancario.reports.dto.BalanceReportDTO;
import com.bancario.reports.dto.TransactionResponse;
import io.smallrye.mutiny.Uni;
import java.util.List;

public interface ReportsService {

    /**
     * Obtiene una lista de saldos disponibles para todas las cuentas de un cliente.
     *
     * @param customerId El ID del cliente.
     * @return Un Uni que emite una lista de BalanceReportDTO.
     */
    Uni<List<BalanceReportDTO>> getBalancesByCustomer(String customerId);

    /**
     * Obtiene una lista de todos los movimientos para un producto bancario específico.
     * @param accountId El ID del producto bancario (cuenta o tarjeta).
     * @return Uni que emite una lista de TransactionResponse.
     */
    Uni<List<TransactionResponse>> getTransactionsByAccountId(String accountId);
}