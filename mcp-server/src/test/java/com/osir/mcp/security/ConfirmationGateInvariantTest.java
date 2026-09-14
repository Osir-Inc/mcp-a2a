package com.osir.mcp.security;

import io.quarkiverse.mcp.server.Tool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Must-be-zero invariant: destructive or financial tools that execute without staging = 0.
 * deleteSshKey slipped through by hand once (2026-09-14); this checks every tool, not a sample.
 */
class ConfirmationGateInvariantTest {

    private static final Path SERVERS = Path.of("src/main/java/com/osir/mcp");

    // A tool "stages" if its source calls pendingActionStore.stage("<toolName>", ...). Source-level on
    // purpose: osirAppMoveToOwned returns Object and stages only when it has to order a VPS.
    private static final Pattern STAGE_CALL = Pattern.compile("pendingActionStore\\.stage\\(\\s*\"([A-Za-z]+)\"");

    // ponytail: financial tools are named, not inferred. A new billable tool must be added here
    // (and stage) or the drift assertion below does not cover it.
    private static final Set<String> GATED = Set.of(
            // money
            "orderVps", "payInvoice", "createPaymentSession", "registerDomain", "renewDomain",
            "transferDomain", "initiateTransfer", "createMailbox", "changeVpsPaymentTerm", "osirAppMoveToOwned",
            // data loss
            "deleteSshKey", "deleteContact", "osirAppDelete", "deleteDnsRecord", "unlockDomain",
            "deleteHost", "deleteMailbox", "cancelTransfer", "buildVpsInstance", "deleteVpsInstance");

    @Test
    void everyDestructiveToolStages_andTheGatedSetIsExactlyWhatWeExpect() throws IOException {
        Set<String> staged = new TreeSet<>();
        List<Method> tools = new ArrayList<>();
        try (var files = Files.list(SERVERS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith("MCPServer.java")).toList()) {
                Matcher m = STAGE_CALL.matcher(Files.readString(p));
                while (m.find()) staged.add(m.group(1));
                String cls = p.getFileName().toString().replace(".java", "");
                Arrays.stream(load("com.osir.mcp." + cls).getDeclaredMethods())
                        .filter(x -> x.isAnnotationPresent(Tool.class))
                        .forEach(tools::add);
            }
        }

        List<String> destructiveButUnstaged = tools.stream()
                .filter(m -> m.getAnnotation(Tool.class).annotations().destructiveHint())
                .map(Method::getName)
                .filter(n -> !staged.contains(n) && !n.equals("executeConfirmedAction"))
                .sorted()
                .toList();

        assertEquals(List.of(), destructiveButUnstaged, "destructive tools that bypass the confirmation gate");
        assertEquals(new TreeSet<>(GATED), staged, "staged tool set drifted; change GATED deliberately");
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
