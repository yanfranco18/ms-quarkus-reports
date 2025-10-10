package com.bancario.reports.service.impl;

import com.bancario.reports.client.AccountServiceRestClient;
import com.bancario.reports.client.TransactionsServiceRestClient;
import com.bancario.reports.dto.*;
import com.bancario.reports.enums.ProductType;
import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class ReportsServiceImpl implements ReportsService {

    @Inject
    @RestClient
    AccountServiceRestClient accountServiceRestClient;

    @Inject
    @RestClient
    TransactionsServiceRestClient transactionsServiceRestClient;

    @Override
    public Uni<List<BalanceReportDTO>> getBalancesByCustomer(String customerId) {
        log.info("Starting report generation for customer with ID: {}", customerId);

        return accountServiceRestClient.getAccountsByCustomer(customerId)
                .onItem().transform(accounts -> {
                    log.info("Found {} accounts for customer ID: {}", accounts.size(), customerId);

                    return accounts.stream()
                            .map(this::mapToBalanceReportDTO)
                            .collect(Collectors.toList());
                });
    }

    @Override
    public Uni<List<TransactionResponse>> getTransactionsByAccountId(String accountId) {
        log.info("Starting transaction report for account ID: {}", accountId);

        // Se llama al cliente REST para obtener los movimientos.
        return transactionsServiceRestClient.getTransactionsByAccountId(accountId)
                .onItem().invoke(transactions -> {
                    log.info("Found {} transactions for account ID: {}", transactions.size(), accountId);
                });
    }

    @Override
    public Uni<List<CommissionReportItem>> generateCommissionsReport(LocalDate startDate, LocalDate endDate) {

        log.info("SERVICE | Iniciando generación de reporte para rango: {} a {}", startDate, endDate);

        // 1. Orquestación: Consumir el Transaction-Service para obtener los datos detallados
        return transactionsServiceRestClient.getCommissionsReportData(startDate, endDate)

                // 2. Lógica de negocio: Aplicar la agregación (Manejo de Responsabilidad Única)
                .onItem().transform(this::aggregateCommissions)

                .onFailure().invoke(e -> {
                    log.error("SERVICE | Fallo al obtener o agregar datos de comisiones: {}", e.getMessage(), e);
                })
                .onFailure().transform(e ->
                        new RuntimeException("Error en la fuente de datos (Transaction Service).", e)
                );
    }

    /**
     * Genera el reporte del Saldo Promedio Diario (SPD) para un cliente de forma reactiva.
     * * Este método orquesta el proceso: realiza la validación inicial, delega la obtención del
     * historial de saldos al microservicio Account a través del Rest Client,
     * maneja fallos de comunicación y ejecuta la lógica de cálculo del promedio.
     *
     * @param customerId El ID del cliente a consultar.
     * @param startDate La fecha de inicio del periodo.
     * @param endDate La fecha de fin del periodo.
     * @return Uni que emite el reporte final DailyAverageBalanceReportDto con el resultado del cálculo.
     * @throws IllegalArgumentException Si el ID del cliente o el rango de fechas es inválido (Mapeado a 400 por el Resource).
     */
    @Override
    public Uni<DailyAverageBalanceReportDto> generateDailyAverageBalanceReport(
            String customerId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 1. Validación de Entrada (Lanzando IllegalArgumentException para mapeo 400)
        validateReportDates(customerId, startDate, endDate);

        log.info("SPD Cálculo iniciado para customerId: {}, rango: [{} - {}]",
                customerId, startDate, endDate);

        // 2. Orquestación: Obtener el historial del account-service
        return accountServiceRestClient.getDailyBalancesByCustomer(customerId, startDate, endDate)

                // 3. Manejo de Fallos en el Cliente REST
                .onFailure().invoke(failure -> {
                    // Registra fallos de red o errores 5xx del servicio externo
                    log.error("Fallo al obtener historial de saldos del account-service para {}. Causa: {}",
                            customerId, failure.getMessage());
                })

                // 4. Ejecución de la Lógica de Negocio (Cálculo del SPD)
                .onItem().transform(historyList ->
                        calculateDailyAverage(customerId, startDate, endDate, historyList)
                );
    }

    /**
     * Valida la integridad y la existencia de los parámetros de entrada del reporte.
     * Su existencia asegura que la lógica de validación se separa de la orquestación (SRP).
     * * @param customerId El ID del cliente.
     * @param startDate La fecha de inicio.
     * @param endDate La fecha de fin.
     * @throws IllegalArgumentException Si los datos son nulos o el rango es inconsistente.
     */
    private void validateReportDates(String customerId, LocalDate startDate, LocalDate endDate) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID de cliente es obligatorio.");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("El rango de fechas es inválido.");
        }
    }

    /**
     * Ejecuta el cálculo central del Saldo Promedio Diario (SPD) a partir del historial de saldos.
     * La fórmula utilizada es: (Suma de saldos finales diarios PASIVOS) / (Número de días en el periodo).
     * * @param customerId El ID del cliente.
     * @param startDate La fecha de inicio del periodo.
     * @param endDate La fecha de fin del periodo.
     * @param historyList Lista de snapshots diarios obtenidos del account-service.
     * @return El DTO de reporte final con el saldo promedio calculado.
     */
    private DailyAverageBalanceReportDto calculateDailyAverage(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            List<DailyBalanceHistoryDto> historyList
    ) {
        if (historyList.isEmpty()) {
            log.warn("SPD: No se encontró historial para customerId {} en el rango.", customerId);
            // Si no hay historial, el promedio es cero.
            return new DailyAverageBalanceReportDto(customerId, startDate, endDate, BigDecimal.ZERO);
        }

        // 1. Determinar el número de días en el periodo
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal divisor = BigDecimal.valueOf(totalDays);

        // 2. Sumar los saldos finales diarios (solo productos 'PASSIVE' - Depósitos)
        BigDecimal totalDailyBalances = historyList.stream()
                .filter(dto -> "PASSIVE".equals(dto.productType()))
                .map(this::mapToEffectiveBalance) // Obtiene el saldo efectivo
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Cálculo final del promedio
        BigDecimal averageBalance = totalDailyBalances.divide(
                divisor,
                2, // Escala a 2 decimales para dinero
                RoundingMode.HALF_UP
        );

        log.info("SPD Calculado para {}: Suma Total: {} / Días: {} = {}",
                customerId, totalDailyBalances, totalDays, averageBalance);
        return new DailyAverageBalanceReportDto(customerId, startDate, endDate, averageBalance);
    }

    /**
     * Aplica la lógica de negocio para determinar qué parte del snapshot diario (balanceEOD o amountUsedEOD)
     * debe contarse para el cálculo de la suma del Saldo Promedio Diario (SPD).
     * * @param dto El DTO de historial de saldo de un día.
     * @return El valor de saldo efectivo que se sumará a la cuenta (BigDecimal.ZERO si es un producto 'ACTIVE').
     */
    private BigDecimal mapToEffectiveBalance(DailyBalanceHistoryDto dto) {
        // Para el Saldo Promedio Diario (SPD), solo los saldos de Depósito (PASSIVE) contribuyen.

        if ("PASSIVE".equals(dto.productType())) {
            // Se suma el saldo de la cuenta de depósito al final del día.
            return Optional.ofNullable(dto.balanceEOD()).orElse(BigDecimal.ZERO);
        } else if ("ACTIVE".equals(dto.productType())) {
            // Los créditos generalmente no cuentan como saldo a favor en el SPD (se ignoran).
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private BalanceReportDTO mapToBalanceReportDTO(AccountResponse account) {
        log.debug("Mapping account {} with type {}", account.id(), account.productType());

        BigDecimal availableBalance;
        if (account.productType() == ProductType.ACTIVE) {
            // Verifica si amountUsed es nulo y, si lo es, usa BigDecimal.ZERO.
            BigDecimal amountUsed = (account.amountUsed() != null) ? account.amountUsed() : BigDecimal.ZERO;
            availableBalance = account.balance().subtract(amountUsed);
            log.debug("Calculated active product balance: {}", availableBalance);
        } else {
            availableBalance = account.balance();
            log.debug("Calculated passive product balance: {}", availableBalance);
        }

        return new BalanceReportDTO(
                account.id(),
                account.productType(),
                availableBalance
        );
    }

    /**
     * Aplica la lógica de GroupBy y Sum para transformar la lista detallada en el reporte final.
     * @param detailedCommissions Lista de comisiones brutas recibidas del Transaction-Service.
     * @return Lista de CommissionReportItem sumados.
     */
    private List<CommissionReportItem> aggregateCommissions(List<CommissionReportDto> detailedCommissions) {

        if (detailedCommissions.isEmpty()) {
            log.info("SERVICE | No se encontraron comisiones en el rango especificado.");
            return List.of();
        }

        log.debug("SERVICE | {} registros detallados recibidos. Iniciando agregación.", detailedCommissions.size());

        // A. Agrupar los DTOs por productName
        Map<String, List<CommissionReportDto>> groupedByProductName = detailedCommissions.stream()
                .collect(Collectors.groupingBy(CommissionReportDto::productName));

        // B. Mapear y Sumar cada grupo
        return groupedByProductName.entrySet().stream()
                .map(entry -> {
                    String productName = entry.getKey();
                    List<CommissionReportDto> list = entry.getValue();

                    // Sumar las comisiones de todos los items en este grupo
                    BigDecimal totalFees = list.stream()
                            .map(CommissionReportDto::fee)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Extraer ProductType (asumiendo consistencia dentro del grupo)
                    ProductType productType = list.getFirst().productType();

                    return CommissionReportItem.builder()
                            .productName(productName)
                            .productType(productType)
                            .totalFees(totalFees)
                            .build();
                })
                .collect(Collectors.toList());
    }
}