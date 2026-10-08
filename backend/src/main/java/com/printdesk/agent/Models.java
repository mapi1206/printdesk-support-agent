package com.printdesk.agent;

/**
 * Model choice per job. Overridable with env vars so the code never needs to change
 * when a newer model ships.
 */
public final class Models {
    private Models() {}

    /** Triage and reply drafting: needs careful reasoning and tool use. */
    public static String triage() { return env("PRINTDESK_MODEL", "claude-sonnet-5-5"); }

    /** Short, cheap jobs: rewrite a draft, translate, chat answers. */
    public static String quick() { return env("PRINTDESK_MODEL_QUICK", "claude-haiku-4-5-20251001"); }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
