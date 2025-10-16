package com.bancario.reports.service.impl;

import com.bancario.reports.client.AccountServiceRestClient;
import com.bancario.reports.client.CustomerServiceRestClient;
import com.bancario.reports.client.TransactionsServiceRestClient;
import com.bancario.reports.dto.*;
import com.bancario.reports.enums.ProductType;
import com.bancario.reports.exception.ServiceUnavailableException;
import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

    @Inject
    @RestClient
    CustomerServiceRestClient customerServiceRestClient;

    @Override
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackBalancesByCustomer")
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
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackTransactionsByAccountId")
    public Uni<List<TransactionResponse>> getTransactionsByAccountId(String accountId) {
        log.info("Starting transaction report for account ID: {}", accountId);

        // Se llama al cliente REST para obtener los movimientos.
        return transactionsServiceRestClient.getTransactionsByAccountId(accountId)
                .onItem().invoke(transactions -> {
                    log.info("Found {} transactions for account ID: {}", transactions.size(), accountId);
                });
    }

    @Override
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackCommissionsReport")
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
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackDailyAverageBalanceReport")
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
     * Elabora un resumen consolidado del cliente orquestando llamadas a Customer y Account Services
     * en paralelo para obtener datos personales y de productos.
     * <p>
     * Se aplica Resiliencia:
     * <ul>
     * <li>@Timeout: Usa reports-service.orchestration-summary.ms (2500ms) para proteger contra la latencia combinada.</li>
     * <li>@CircuitBreaker: Aísla el método si la orquestación falla repetidamente.</li>
     * <li>@Fallback: Lanza una ServiceUnavailableException (mapeada a HTTP 503 por el GlobalExceptionMapper).</li>
     * </ul>
     *
     * @param customerId El ID del cliente.
     * @return Uni que emite el ConsolidatedSummaryDTO.
     */
    @Override
    @Timeout
    @CircuitBreaker
    @Fallback(fallbackMethod = "fallbackConsolidatedSummary")
    public Uni<ConsolidatedSummaryDTO> getConsolidatedSummary(String customerId) {
        log.info("SERVICE | Iniciando orquestación de resumen consolidado para cliente: {}", customerId);

        // 1. Definir las dos llamadas REST que se ejecutarán en paralelo
        Uni<CustomerResponse> customerUni = customerServiceRestClient.getCustomerById(customerId);
        Uni<List<AccountResponse>> accountsUni = accountServiceRestClient.getAccountsByCustomer(customerId);

        // 2. Orquestación reactiva: Combinar los resultados de forma eficiente
        return Uni.combine().all().unis(customerUni, accountsUni)
                .asTuple()
                .onItem().transform(tuple -> {
                    // Item1 es CustomerResponse, Item2 es List<AccountResponse>
                    CustomerResponse customer = tuple.getItem1();
                    List<AccountResponse> accounts = tuple.getItem2();

                    // 3. Mapeo final: Construir el DTO consolidado
                    log.debug("SERVICE | Consolidando {} productos para cliente {}", accounts.size(), customer.id());
                    return ConsolidatedSummaryDTO.builder()
                            .customerId(customer.id())
                            .fullName(customer.firstName() + " " + customer.lastName())
                            .products(accounts)
                            .processingTimestamp(Instant.now().toString())
                            .build();
                })
                .onFailure().invoke(failure -> {
                    // Log detallado en caso de fallo antes de activar el Fallback
                    log.error("SERVICE | Fallo en la orquestación consolidada para cliente {}. Causa: {}",
                            customerId, failure.getMessage(), failure);
                });
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

    /**
     * Fallback con la firma EXACTA para getBalancesByCustomer (Uni<List<BalanceReportDTO>>).
     */
    public Uni<List<BalanceReportDTO>> fallbackBalancesByCustomer(String customerId, Throwable failure) {
        // Casting explícito: Le decimos al compilador que el Uni devuelto
        // por la función privada es, a efectos de compilación, del tipo correcto.
        @SuppressWarnings("unchecked")
        Uni<List<BalanceReportDTO>> uni = (Uni<List<BalanceReportDTO>>) handleQuickQueryFallback(customerId, failure);
        return uni;
    }

    /**
     * Fallback con la firma EXACTA para getTransactionsByAccountId (Uni<List<TransactionResponse>>).
     */
    public Uni<List<TransactionResponse>> fallbackTransactionsByAccountId(String accountId, Throwable failure) {
        // Casting explícito.
        @SuppressWarnings("unchecked")
        Uni<List<TransactionResponse>> uni = (Uni<List<TransactionResponse>>) handleQuickQueryFallback(accountId, failure);
        return uni;
    }

    /**
     * Lógica común que maneja el fallo de consultas rápidas y lanza la ServiceUnavailableException.
     * El tipo de retorno es genérico (Uni) ya que siempre lanza una excepción.
     */
    private Uni handleQuickQueryFallback(String id, Throwable failure) {
        log.error("FALLBACK ACTIVO (Consulta Rápida) para ID {}. Causa: {}", id, failure.getMessage());
        String errorMessage = "El servicio de reportes rápidos está temporalmente no disponible.";
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }

    //FALLBACK para generateCommissionsReport (Reporte Pesado - HTTP 503)
    public Uni<List<CommissionReportItem>> fallbackCommissionsReport(LocalDate startDate, LocalDate endDate, Throwable failure) {
        log.error("FALLBACK ACTIVO (Comisiones) desde {} hasta {}. Causa: {}", startDate, endDate, failure.getMessage());
        String errorMessage = "El servicio de reporte de comisiones está inoperativo. No se pudieron obtener los datos brutos.";
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }

    //FALLBACK para generateDailyAverageBalanceReport (Orquestación Analítica - HTTP 503)
    public Uni<DailyAverageBalanceReportDto> fallbackDailyAverageBalanceReport(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            Throwable failure
    ) {
        log.error("FALLBACK ACTIVO (SPD) para cliente {}. Causa: {}", customerId, failure.getMessage());
        String errorMessage = "El servicio de reporte SPD está inoperativo. No se pudieron obtener datos históricos.";
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }

    /**
     * Método Fallback de degradación para getConsolidatedSummary.
     * Lanza una excepción global específica que es mapeada a HTTP 503 (Service Unavailable).
     */
    public Uni<ConsolidatedSummaryDTO> fallbackConsolidatedSummary(String customerId, Throwable failure) {
        log.warn("FALLBACK ACTIVO | Resumen Consolidado para cliente {}. Causa: {}", customerId, failure.getMessage());
        String errorMessage = "El servicio de resumen consolidado está inoperativo. No se pudo completar la orquestación de datos.";
        return Uni.createFrom().failure(new ServiceUnavailableException(errorMessage, failure));
    }
}