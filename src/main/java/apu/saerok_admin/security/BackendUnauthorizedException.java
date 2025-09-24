package apu.saerok_admin.security;

public class BackendUnauthorizedException extends RuntimeException {

    public BackendUnauthorizedException(String message) {
        super(message);
    }

    public BackendUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
