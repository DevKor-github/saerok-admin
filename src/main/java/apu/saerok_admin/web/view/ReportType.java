package apu.saerok_admin.web.view;

public enum ReportType {
    COLLECTION("새록", "primary"),
    COMMENT("댓글", "warning");

    private final String displayName;
    private final String badgeVariant;

    ReportType(String displayName, String badgeVariant) {
        this.displayName = displayName;
        this.badgeVariant = badgeVariant;
    }

    public String displayName() {
        return displayName;
    }

    public String badgeVariant() {
        return badgeVariant;
    }
}
