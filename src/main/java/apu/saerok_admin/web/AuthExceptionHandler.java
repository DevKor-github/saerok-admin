package apu.saerok_admin.web;

import apu.saerok_admin.security.BackendUnauthorizedException;
import apu.saerok_admin.security.LoginSessionManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AuthExceptionHandler {

    private final LoginSessionManager loginSessionManager;

    public AuthExceptionHandler(LoginSessionManager loginSessionManager) {
        this.loginSessionManager = loginSessionManager;
    }

    @ExceptionHandler(BackendUnauthorizedException.class)
    public String handleBackendUnauthorizedException() {
        loginSessionManager.clearCurrentSession();
        return "redirect:/login?error=session";
    }
}
