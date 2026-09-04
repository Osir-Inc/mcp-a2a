package com.osir.mcp;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Every no-arg PromptMessage method must be a REGISTERED prompt that actually returns content.
     * Deliberately no expected count: a hardcoded number is an inventory that goes stale the day
     * someone adds a prompt, and it fails for the wrong reason when they do.
     */
    @Test
    void everyPromptMethodIsRegisteredAndReturnsContent() {
        List<Method> promptMethods = Arrays.stream(PromptsMCPServer.class.getMethods())
                .filter(m -> m.getReturnType() == PromptMessage.class)
                .filter(m -> m.getParameterCount() == 0)
                .toList();

        assertFalse(promptMethods.isEmpty(), "no no-arg prompt methods found - wrong class?");
        for (Method m : promptMethods) {
            assertTrue(m.isAnnotationPresent(Prompt.class),
                    m.getName() + " returns a PromptMessage but is not annotated @Prompt, so no client can call it");
            try {
                assertNotNull(m.invoke(prompts), m.getName() + " returned null");
            } catch (Exception e) {
                fail(m.getName() + " threw: " + e.getMessage());
            }
        }
    }
}
