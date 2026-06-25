package com.osir.mcp;

import io.quarkiverse.mcp.server.PromptMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PromptsMCPServerTest {

    PromptsMCPServer prompts;

    @BeforeEach
    void setUp() {
        prompts = new PromptsMCPServer();
    }

    @Test
    void gettingStarted_returnsContent() {
        PromptMessage msg = prompts.gettingStarted();
        assertNotNull(msg);
    }

    @Test
    void vpsSetupGuide_returnsContent() {
        PromptMessage msg = prompts.vpsSetupGuide();
        assertNotNull(msg);
    }

    @Test
    void dnsSetupGuide_returnsContent() {
        PromptMessage msg = prompts.dnsSetupGuide();
        assertNotNull(msg);
    }

    @Test
    void billingOverview_returnsContent() {
        PromptMessage msg = prompts.billingOverview();
        assertNotNull(msg);
    }

    @Test
    void domainManagementGuide_returnsContent() {
        PromptMessage msg = prompts.domainManagementGuide();
        assertNotNull(msg);
    }

    @Test
    void hostingComparison_returnsContent() {
        PromptMessage msg = prompts.hostingComparison();
        assertNotNull(msg);
    }

    @Test
    void troubleshooting_returnsContent() {
        PromptMessage msg = prompts.troubleshooting();
        assertNotNull(msg);
    }

    @Test
    void securityBestPractices_returnsContent() {
        PromptMessage msg = prompts.securityBestPractices();
        assertNotNull(msg);
    }

    @Test
    void allPromptMethods_returnNonNull() {
        // Verify every public method that returns PromptMessage works
        long promptCount = Arrays.stream(PromptsMCPServer.class.getMethods())
                .filter(m -> m.getReturnType() == PromptMessage.class)
                .filter(m -> m.getParameterCount() == 0)
                .map(m -> {
                    try {
                        assertNotNull(m.invoke(prompts), m.getName() + " returned null");
                        return m;
                    } catch (Exception e) {
                        fail(m.getName() + " threw: " + e.getMessage());
                        return null;
                    }
                })
                .count();
        assertEquals(8, promptCount, "Expected 8 no-arg prompt methods");
    }
}
