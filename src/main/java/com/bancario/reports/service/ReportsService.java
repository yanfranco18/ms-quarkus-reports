package com.bancario.reports.service;

import com.bancario.reports.dto.BalanceReportDTO;
import com.bancario.reports.dto.CommissionReportItem;
import com.bancario.reports.dto.TransactionResponse;
import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
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

    /**
     * Genera el reporte agregado de comisiones, agrupado por producto,
     * consumiendo datos del Transaction-Service.
     * * @param startDate La fecha de inicio del periodo.
     * @param endDate La fecha de fin del periodo.
     * @return Uni que emite una lista del reporte final agregado (CommissionReportItem).
     */
    Uni<List<CommissionReportItem>> generateCommissionsReport(LocalDate startDate, LocalDate endDate);
}