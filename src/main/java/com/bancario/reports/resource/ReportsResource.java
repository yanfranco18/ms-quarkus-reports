package com.bancario.reports.resource;

import com.bancario.reports.dto.CommissionReportItem;
import com.bancario.reports.dto.ConsolidatedSummaryDTO;
import com.bancario.reports.dto.DailyAverageBalanceReportDto;
import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;

@Slf4j
@Path("/reports")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Operaciones para generar reportes.")
public class ReportsResource {

    @Inject
    ReportsService reportsService;

    @GET
    @Path("/balances")
    @Operation(summary = "Obtener saldos disponibles", description = "Consulta todos los saldos de las cuentas y tarjetas de un cliente.")
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Consulta de saldos exitosa",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            @APIResponse(responseCode = "400", description = "ID de cliente inválido."),
            @APIResponse(responseCode = "404", description = "No se encontraron cuentas para el cliente."),
            @APIResponse(responseCode = "500", description = "Error interno del servidor.")
    })
    public Uni<Response> getBalancesByCustomer(
            @Parameter(description = "ID del cliente.", required = true, example = "ejemplo123")
            @QueryParam("customerId") String customerId) {

        log.info("Received request to get balances for customer ID: {}", customerId);

        if (customerId == null || customerId.isBlank()) {
            log.error("Customer ID is null or blank.");
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Customer ID must not be empty.").build());
        }

        return reportsService.getBalancesByCustomer(customerId)
                .onItem().transform(balances -> {
                    if (balances.isEmpty()) {
                        log.warn("No accounts found for customer ID: {}", customerId);
                        return Response.status(Response.Status.NOT_FOUND).entity("No accounts found.").build();
                    }
                    log.info("Successfully retrieved balances for customer ID: {}", customerId);
                    return Response.ok(balances).build();
                })
                .onFailure().recoverWithItem(error -> {
                    log.error("An error occurred while retrieving balances: {}", error.getMessage());
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An unexpected error occurred.").build();
                });
    }

    @GET
    @Path("/movements")
    @Operation(summary = "Obtener transacciones por cuenta", description = "Consulta todos los movimientos de una cuenta bancaria o tarjeta de crédito.")
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Transacciones consultadas con éxito",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON)
            ),
            @APIResponse(responseCode = "400", description = "ID de cuenta inválido."),
            @APIResponse(responseCode = "404", description = "No se encontraron transacciones para la cuenta."),
            @APIResponse(responseCode = "500", description = "Error interno del servidor.")
    })
    public Uni<Response> getTransactionsByAccountId(
            @Parameter(description = "ID del producto bancario (cuenta).", required = true)
            @QueryParam("accountId") String accountId) {

        log.info("Received request to get transactions for account ID: {}", accountId);

        if (accountId == null || accountId.isBlank()) {
            log.error("Account ID is null or blank.");
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Account ID must not be empty.").build());
        }

        return reportsService.getTransactionsByAccountId(accountId)
                .onItem().transform(transactions -> {
                    if (transactions.isEmpty()) {
                        log.warn("No transactions found for account ID: {}", accountId);
                        return Response.status(Response.Status.NOT_FOUND).entity("No transactions found.").build();
                    }
                    log.info("Successfully retrieved {} transactions for account ID: {}", transactions.size(), accountId);
                    return Response.ok(transactions).build();
                })
                .onFailure().recoverWithItem(error -> {
                    log.error("An error occurred while retrieving transactions: {}", error.getMessage());
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An unexpected error occurred.").build();
                });
    }

    @GET
    @Path("/commissions")
    @Operation(summary = "Generar reporte agregado de comisiones por producto",
            description = "Consulta la suma total de las comisiones cobradas, agrupadas por producto, dentro de un rango de fechas.")
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Reporte de comisiones generado con éxito",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = CommissionReportItem.class))
            ),
            @APIResponse(responseCode = "400", description = "Fechas inválidas, nulas o rango incorrecto (startDate posterior a endDate)."),
            @APIResponse(responseCode = "404", description = "No se encontraron comisiones en el periodo."),
            @APIResponse(responseCode = "500", description = "Error interno del servidor o fallo de comunicación con el Transaction Service.")
    })
    public Uni<Response> getAggregatedCommissionsReport(
            @Parameter(description = "Fecha de inicio del periodo (YYYY-MM-DD)", required = true, example = "2025-01-01")
            @RestQuery LocalDate startDate,

            @Parameter(description = "Fecha de fin del periodo (YYYY-MM-DD)", required = true, example = "2025-01-31")
            @RestQuery LocalDate endDate) {

        log.info("API | Solicitud de reporte de comisiones recibida. Rango: {} a {}", startDate, endDate);

        // 1. Validación de Entrada (Lógica Fail Fast)
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            log.warn("API | Validación fallida: Rango de fechas inválido. Inicio: {}, Fin: {}", startDate, endDate);
            String message = (startDate == null || endDate == null)
                    ? "startDate y endDate son requeridos."
                    : "startDate no puede ser posterior a endDate.";

            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(message).build());
        }
        // 2. Delegación a la Capa de Servicio
        return reportsService.generateCommissionsReport(startDate, endDate)
                .onItem().transform(reportItems -> {

                    if (reportItems.isEmpty()) {
                        log.warn("API | No se encontraron comisiones en el rango {} a {}", startDate, endDate);
                        return Response.status(Response.Status.NOT_FOUND).entity("No se encontraron comisiones para generar el reporte.").build();
                    }

                    log.info("API | Reporte generado con éxito. {} productos agregados.", reportItems.size());
                    return Response.ok(reportItems).build();
                })
                .onFailure().recoverWithItem(error -> {
                    // 3. Manejo de Errores (Error de servicio/comunicación)
                    log.error("API | Error interno al generar el reporte: {}", error.getMessage(), error);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Error al procesar el reporte: " + error.getMessage())
                            .build();
                });
    }

    /**
     * Endpoint para generar el reporte del Saldo Promedio Diario (SPD) de un cliente
     * para un rango de fechas.
     * La respuesta es reactiva y las excepciones (ej. 400 Bad Request) son manejadas
     * por el Global Exception Mapper del framework.
     * * @param customerId El ID del cliente.
     * @param startDate La fecha de inicio del rango (formato YYYY-MM-DD).
     * @param endDate La fecha de fin del rango (formato YYYY-MM-DD).
     * @return Uni que emite el reporte final DailyAverageBalanceReportDto.
     */
    @GET
    @Path("/daily-average-balance")
    @Operation(summary = "Calcula el Saldo Promedio Diario (SPD) para un cliente.",
            description = "Orquesta la obtención del historial y el cálculo del promedio.")
    @APIResponse(responseCode = "200", description = "Reporte SPD generado con éxito.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = DailyAverageBalanceReportDto.class)))
    @APIResponse(responseCode = "400", description = "Parámetros de consulta o fechas inválidas.")
    @APIResponse(responseCode = "500", description = "Fallo interno durante la orquestación o cálculo.")
    public Uni<DailyAverageBalanceReportDto> calculateDailyAverageBalance(
            @Parameter(description = "ID único del cliente.")
            @QueryParam("customerId")
            String customerId,

            @Parameter(description = "Fecha de inicio del periodo (YYYY-MM-DD).")
            @QueryParam("startDate")
            LocalDate startDate,

            @Parameter(description = "Fecha de fin del periodo (YYYY-MM-DD).")
            @QueryParam("endDate")
            LocalDate endDate
    ) {
        log.info("SPD Resource: Solicitud de cálculo recibida para customerId: {}", customerId);
        return reportsService.generateDailyAverageBalanceReport(customerId, startDate, endDate)
                .onItem().invoke(report -> {
                    log.debug("SPD Resource: Cálculo finalizado. Promedio retornado: {}", report.dailyAverageBalance());
                });
    }

    /**
     * Endpoint para obtener un resumen consolidado de un cliente, incluyendo datos personales
     * y todos los productos bancarios asociados (cuentas).
     *
     * @param customerId El ID único del cliente.
     * @return Uni que emite una respuesta HTTP 200 con el ConsolidatedSummaryDTO o un error 503/400.
     */
    @GET
    @Path("/consolidated/{customerId}")
    @Operation(
            summary = "Obtener Resumen Consolidado del Cliente",
            description = "Orquesta llamadas paralelas a Customer Service y Account Service para devolver un resumen integral del cliente y sus productos."
    )
    @APIResponse(
            responseCode = "200",
            description = "Resumen consolidado generado exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ConsolidatedSummaryDTO.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Parámetros de entrada inválidos (e.g., customerId nulo o formato incorrecto)."
    )
    @APIResponse(
            responseCode = "503",
            description = "Fallo de Resiliencia: La orquestación ha superado el Timeout o el Circuit Breaker está abierto."
    )
    public Uni<Response> getConsolidatedSummary(@PathParam("customerId") @NotNull String customerId) {
        if (customerId.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("El ID del cliente no puede ser vacío.").build());
        }
        return reportsService.getConsolidatedSummary(customerId)
                .onItem().transform(summary -> Response.ok(summary).build());
    }
}