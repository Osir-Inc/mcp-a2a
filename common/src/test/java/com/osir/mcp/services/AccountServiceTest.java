package com.osir.mcp.services;

import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.clients.TransferBackendClient;
import com.osir.mcp.clients.VpsBackendClient;
import com.osir.mcp.models.account.AccountSummaryResult;
import com.osir.mcp.models.account.UserProfile;
import com.osir.mcp.models.account.UserProfileResult;
import com.osir.mcp.models.billing.BalanceResponse;
import com.osir.mcp.models.transfer.PendingTransfer;
import com.osir.mcp.models.vps.VpsCountResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    @Mock
    DomainBackendClient domainBackendClient;

    @Mock
    BillingBackendClient billingBackendClient;

    @Mock
    VpsBackendClient vpsBackendClient;

    @Mock
    TransferBackendClient transferBackendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    AccountService accountService;

    private static final String TEST_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMyProfile_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        UserProfile profile = new UserProfile();
        profile.setCustomerId("cust-1");
        UserProfile.ContactDetails details = new UserProfile.ContactDetails();
        details.setName("John Doe");
        details.setEmail("john@example.com");
        details.setOrganization("OSIR");
        profile.setDetails(details);
        com.osir.mcp.models.account.BalanceInfo bal = new com.osir.mcp.models.account.BalanceInfo();
        bal.setAmountCents(10000); // 10000 cents = $100.00
        bal.setCurrency("USD");
        profile.setBalance(bal);
        profile.setDomainCount(5);
        profile.setVpsCount(2);
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenReturn(profile);

        UserProfileResult result = accountService.getMyProfile();

        assertTrue(result.isSuccess());
        assertEquals("cust-1", result.getCustomerId());
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        assertEquals("OSIR", result.getOrganization());
        assertEquals("100.00", result.getBalance());
        assertEquals("USD", result.getCurrency());
        assertEquals(5, result.getDomainCount());
        assertEquals(2, result.getVpsCount());
    }

    @Test
    void getMyProfile_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        UserProfileResult result = accountService.getMyProfile();
        assertFalse(result.isSuccess());
    }

    @Test
    void getMyProfile_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenThrow(new RuntimeException("Backend error"));

        UserProfileResult result = accountService.getMyProfile();
        assertFalse(result.isSuccess());
    }

    @Test
    void getAccountSummary_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        UserProfile profile = new UserProfile();
        profile.setCustomerId("cust-1");
        UserProfile.ContactDetails details2 = new UserProfile.ContactDetails();
        details2.setName("John Doe");
        details2.setEmail("john@example.com");
        profile.setDetails(details2);
        profile.setDomainCount(5);
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenReturn(profile);

        BalanceResponse balance = new BalanceResponse();
        balance.setBalance("250.00");
        balance.setCurrency("USD");
        when(billingBackendClient.getAccountBalance(TEST_TOKEN)).thenReturn(balance);

        VpsCountResult vpsCount = new VpsCountResult(true, "OK", 3);
        when(vpsBackendClient.getVpsInstanceCount(TEST_TOKEN)).thenReturn(vpsCount);

        PendingTransfer pt = new PendingTransfer();
        pt.setDomain("example.com");
        var transferResponse = new com.osir.mcp.models.transfer.PendingTransferListApiResponse();
        transferResponse.setTransfers(List.of(pt));
        when(transferBackendClient.listPendingTransfers(TEST_TOKEN)).thenReturn(transferResponse);

        AccountSummaryResult result = accountService.getAccountSummary();

        assertTrue(result.isSuccess());
        assertEquals("cust-1", result.getCustomerId());
        assertEquals("John Doe", result.getName());
        assertEquals("250.00", result.getBalance());
        assertEquals("USD", result.getCurrency());
        assertEquals(5, result.getDomainCount());
        assertEquals(3, result.getVpsCount());
        assertEquals(1, result.getPendingTransferCount());
        assertEquals("example.com", result.getPendingTransferDomains().get(0));
    }

    @Test
    void getAccountSummary_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        AccountSummaryResult result = accountService.getAccountSummary();
        assertFalse(result.isSuccess());
    }

    @Test
    void getAccountSummary_partialFailure() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        UserProfile profile = new UserProfile();
        profile.setCustomerId("cust-1");
        UserProfile.ContactDetails details3 = new UserProfile.ContactDetails();
        details3.setName("John Doe");
        profile.setDetails(details3);
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenReturn(profile);

        // Balance fails
        when(billingBackendClient.getAccountBalance(TEST_TOKEN)).thenThrow(new RuntimeException("Balance error"));

        // VPS count fails
        when(vpsBackendClient.getVpsInstanceCount(TEST_TOKEN)).thenThrow(new RuntimeException("VPS error"));

        // Transfers succeed
        var emptyTransfers = new com.osir.mcp.models.transfer.PendingTransferListApiResponse();
        emptyTransfers.setTransfers(List.of());
        when(transferBackendClient.listPendingTransfers(TEST_TOKEN)).thenReturn(emptyTransfers);

        AccountSummaryResult result = accountService.getAccountSummary();

        // Should still succeed with partial data
        assertTrue(result.isSuccess());
        assertEquals("cust-1", result.getCustomerId());
        assertEquals(0, result.getPendingTransferCount());
    }
}
