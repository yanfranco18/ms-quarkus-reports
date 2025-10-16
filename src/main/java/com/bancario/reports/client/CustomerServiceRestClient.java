package com.bancario.reports.client;

import com.bancario.reports.dto.CustomerResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/customers")
@RegisterRestClient(configKey = "customer-service")
public interface CustomerServiceRestClient {

    @GET
    @Path("/{customerId}")
    Uni<CustomerResponse> getCustomerById(@PathParam("customerId") String customerId);
}
