package com.osir.mcp.security;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAuth {

    /** Canonical name of the optional per-call session key tool argument. */
    String SESSION_KEY = "sessionKey";

    /** Shared @ToolArg description for the session key argument. */
    String SESSION_KEY_DESC = "Session key (osk_...) returned by checkDeviceLoginStatus. "
            + "Pass it on every call when logged in via the in-chat device flow; omit when connected via OAuth.";
}
