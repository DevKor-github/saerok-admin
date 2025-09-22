package apu.saerok_admin.web.view;

public record Breadcrumb(String label, String href, boolean active) {
    public static Breadcrumb of(String label, String href) {
        return new Breadcrumb(label, href, false);
    }

    public static Breadcrumb active(String label) {
        return new Breadcrumb(label, null, true);
    }
}
