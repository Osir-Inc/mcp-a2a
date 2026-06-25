package com.osir.mcp.clients;

import com.osir.mcp.models.billing.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BillingBackendClient {

    @GET
    @Path("/v1/payment/balance")
    BalanceResponse getAccountBalance(
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/billing/invoices")
    InvoiceListResponse listInvoices(
            @QueryParam("status") String status,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/billing/invoices/{invoiceId}")
    InvoiceDetailResponse getInvoiceDetails(
            @PathParam("invoiceId") String invoiceId,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/billing/invoices/{invoiceId}/pay")
    PayInvoiceResponse payInvoice(
            @PathParam("invoiceId") String invoiceId,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/billing/invoices/statistics")
    InvoiceStatisticsResponse getInvoiceStatistics(
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/payment/checkout-session")
    PaymentSessionResponse createPaymentSession(
            PaymentSessionRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/payment/balance/history")
    TransactionListResponse getPaymentTransactions(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/payment/fee-preview")
    FeePreviewResponse previewPaymentFees(
            @QueryParam("amount") Double amount,
            @QueryParam("currency") String currency,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/public/catalog/domains")
    List<PricingEntry> getDomainPricing(
            @QueryParam("extension") String extension,
            @HeaderParam("Authorization") String bearerToken
    );
}
