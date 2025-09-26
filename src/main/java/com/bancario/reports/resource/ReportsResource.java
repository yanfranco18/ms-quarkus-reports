package com.bancario.reports.resource;

import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
}