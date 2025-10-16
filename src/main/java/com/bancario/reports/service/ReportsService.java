package com.bancario.reports.service;

import com.bancario.reports.dto.*;
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

    /**
     * Genera el reporte del Saldo Promedio Diario (SPD) para un cliente,
     * orquestando la obtención del historial de saldos y realizando el cálculo final.
     *
     * @param customerId El ID del cliente a consultar.
     * @param startDate La fecha de inicio del periodo.
     * @param endDate La fecha de fin del periodo.
     * @return Uni que emite el reporte final DailyAverageBalanceReportDto.
     * @throws IllegalArgumentException Si alguno de los parámetros de entrada es inválido.
     */
    Uni<DailyAverageBalanceReportDto> generateDailyAverageBalanceReport(
            String customerId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Elabora un resumen consolidado del cliente, orquestando las llamadas a Customer y Account Services
     * para obtener datos personales y de productos.
     *
     * @param customerId El ID del cliente.
     * @return Uni que emite el ConsolidatedSummaryDTO.
     */
    Uni<ConsolidatedSummaryDTO> getConsolidatedSummary(String customerId);
}