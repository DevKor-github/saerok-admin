package apu.saerok_admin.web.view;

public record ToastMessage(String id, String title, String body, String variant, boolean autohide) {
    public static ToastMessage success(String id, String title, String body) {
        return new ToastMessage(id, title, body, "success", true);
    }

    public static ToastMessage info(String id, String title, String body) {
        return new ToastMessage(id, title, body, "info", true);
    }
}
