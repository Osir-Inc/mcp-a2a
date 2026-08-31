package com.osir.mcp;

import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.design.DesignBriefResult;
import com.osir.mcp.services.DeploymentService;
import com.osir.mcp.services.DesignBriefService;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteDesignMCPServerTest {

    @Mock
    DesignBriefService designBriefService;

    @Mock
    DeploymentService deploymentService;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    WebsiteDesignMCPServer server;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    @Test
    void websiteDesigner_promptMentionsBothTools() {
        String text = ((io.quarkiverse.mcp.server.TextContent) server.websiteDesigner().content()).text();
        assertTrue(text.contains("osirSiteDesignBrief"));
        assertTrue(text.contains("osirSitePublish"));
    }

    @Test
    void osirSiteDesignBrief_delegatesToService() {
        DesignBriefResult expected = new DesignBriefResult(true, "ok", "p", "e", null);
        when(designBriefService.build("B", "w", "a", "other", "Go", null)).thenReturn(expected);

        assertSame(expected, server.osirSiteDesignBrief("B", "w", "a", "other", "Go", null));
    }

    @Test
    void osirSiteDesignBrief_handlesException() {
        when(designBriefService.build(any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Fail"));

        DesignBriefResult r = server.osirSiteDesignBrief("B", "w", "a", "other", "Go", null);
        assertFalse(r.success());
        assertTrue(r.message().contains("Fail"));
    }

    @Test
    void osirSitePublish_delegatesToService() {
        DeployResult expected = new DeployResult(true, "ok", "app-1", "https://x.osir.app", "DEPLOYING");
        when(deploymentService.publishStatic("x", "<html>", "al", true)).thenReturn(expected);

        assertSame(expected, server.osirSitePublish("x", "<html>", "al", true, null, mockConnection));
        verify(deploymentService).publishStatic("x", "<html>", "al", true);
    }

    @Test
    void osirSitePublish_nullDesignContractMeansOwnSite() {
        DeployResult expected = new DeployResult(true, "ok", "app-1", "https://x.osir.app", "DEPLOYING");
        when(deploymentService.publishStatic("x", "<html>", null, false)).thenReturn(expected);

        assertSame(expected, server.osirSitePublish("x", "<html>", null, null, null, mockConnection));
    }

    @Test
    void osirSitePublish_handlesException() {
        when(deploymentService.publishStatic(any(), any(), any(), anyBoolean())).thenThrow(new RuntimeException("Fail"));

        DeployResult r = server.osirSitePublish("x", "<html>", null, null, null, mockConnection);
        assertFalse(r.success());
        assertTrue(r.message().contains("Fail"));
    }
}
