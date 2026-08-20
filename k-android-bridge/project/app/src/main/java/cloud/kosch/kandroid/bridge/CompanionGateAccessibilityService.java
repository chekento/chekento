package cloud.kosch.kandroid.bridge;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

/**
 * Privacy-light companion visibility gate.
 * Reads only AccessibilityEvent package/class/scroll metadata; window content retrieval is disabled.
 */
public class CompanionGateAccessibilityService extends AccessibilityService {
    private static final String LAUNCHER = "com.ss.launcher2";
    private SharedPreferences sp;
    private int launcherPage = 0;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        sp = getSharedPreferences("kbridge", 0);
        launcherPage = sp.getInt("launcherPage", 0);
        sp.edit().putBoolean("gateEnabled", true).apply();
        publish(false, "Gate bereit", false, "");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        if (sp == null) sp = getSharedPreferences("kbridge", 0);

        final String pkg = e.getPackageName().toString();
        final int type = e.getEventType();

        if (isTransientSystemPackage(pkg)) return;

        String label = appLabel(pkg);
        boolean claude = isClaude(pkg, label);

        if (LAUNCHER.equals(pkg)) {
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                int candidate = inferLauncherPage(e);
                if (candidate >= 0) {
                    launcherPage = candidate;
                    sp.edit().putInt("launcherPage", launcherPage).apply();
                }
            }
            boolean visible = launcherPage == 0;
            publish(visible,
                    visible ? "Total Launcher · Companion-Tab" : "Total Launcher · anderer Tab",
                    false, pkg);
            return;
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        boolean extAi = sp.getBoolean("externalAiOverlay", true) && isAiApp(pkg, label);
        publish(extAi,
                extAi ? (label + " · KI-App") : (label + " · Overlay aus"),
                claude, pkg);
    }

    /**
     * Total Launcher emits horizontal scroll metadata on page swipes.
     * Prefer screen-width based scrollX because item indices may belong to page objects.
     */
    private int inferLauncherPage(AccessibilityEvent e) {
        int sx = e.getScrollX();
        int maxX = e.getMaxScrollX();
        int width = Math.max(1, getResources().getDisplayMetrics().widthPixels);

        if (sx >= 0 && maxX > 0) {
            int page = Math.round(sx / (float) width);
            if (page >= 0 && page <= 8) return page;
        }

        int count = e.getItemCount();
        int to = e.getToIndex();
        if (count >= 2 && count <= 8 && to >= 0 && to < count) return to;
        return -1;
    }

    private boolean isTransientSystemPackage(String pkg) {
        String s = pkg.toLowerCase(Locale.ROOT);
        return s.contains("inputmethod") || s.contains("honeyboard")
                || s.contains("systemui") || s.contains("permissioncontroller")
                || s.contains("packageinstaller");
    }

    private void publish(boolean visible, String reason, boolean claude, String pkg) {
        sp.edit()
                .putBoolean("gateVisible", visible)
                .putString("gateReason", reason)
                .putBoolean("claudeForeground", claude)
                .putString("foregroundPkg", pkg == null ? "" : pkg)
                .putLong("gateMs", System.currentTimeMillis())
                .apply();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            return cs == null ? pkg : cs.toString();
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private boolean isClaude(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.ROOT);
        return s.contains("anthropic") || s.contains("claude");
    }

    private boolean isAiApp(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.ROOT);
        return s.contains("chatgpt") || s.contains("openai")
                || s.contains("claude") || s.contains("anthropic")
                || s.contains("gemini") || s.contains("bard")
                || s.contains("grok")
                || s.contains("perplexity")
                || s.contains("meta ai") || s.contains("stella")
                || s.contains("notebooklm") || s.contains("tailwind");
    }

    @Override public void onInterrupt() {
        if (sp != null) publish(false, "Gate unterbrochen", false, "");
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        if (sp != null) {
            sp.edit()
                    .putBoolean("gateEnabled", false)
                    .putBoolean("gateVisible", false)
                    .putBoolean("claudeForeground", false)
                    .putString("gateReason", "Gate deaktiviert")
                    .apply();
        }
        return super.onUnbind(intent);
    }
}
