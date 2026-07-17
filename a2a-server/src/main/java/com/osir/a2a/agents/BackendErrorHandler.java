package com.osir.a2a.agents;

import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.Message;
import com.osir.a2a.protocol.TaskState;

/**
 * Translates backend/service exceptions into user-friendly A2A error messages.
 */
public final class BackendErrorHandler {

    private BackendErrorHandler() {}

    /**
     * Classify an exception and return a user-friendly message.
     */
    public static String classify(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (msg.contains("ConnectException") || msg.contains("Connection refused") || msg.contains("UnknownHost")) {
            return "Backend service is currently unavailable. Please try again later.";
        }
        if (msg.contains("SocketTimeoutException") || msg.contains("Read timed out")) {
            return "Backend service is responding slowly. Please try again later.";
        }
        if (msg.contains("401") || msg.contains("Unauthorized")) {
            return "Authentication failed. Please verify your credentials and try again.";
        }
        if (msg.contains("403") || msg.contains("Forbidden")) {
            return "You do not have permission to perform this operation.";
        }
        if (msg.contains("404") || msg.contains("Not Found")) {
            return "The requested resource was not found.";
        }
        if (msg.contains("500") || msg.contains("Internal Server Error")) {
            return "The backend service encountered an internal error. Please try again later.";
        }

        // Generic fallback — don't leak internal details
        return "An error occurred while processing your request. Please try again.";
    }

    /**
     * Apply classified error to a task.
     */
    public static A2ATask fail(A2ATask task, Exception e) {
        task.transitionTo(TaskState.FAILED);
        task.addMessage(new Message("agent", classify(e)));
        return task;
    }
}
