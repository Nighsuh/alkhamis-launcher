package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * When Google TV opens its own home after the remote's Home button, bring this launcher forward.
 * It does nothing once this launcher is the resolved Home, and it never disables another package.
 */
public class HomeButtonService extends AccessibilityService {
    static HomeButtonService running;
    private long lastOpen;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (weAreHome()) return;
        CharSequence pkg = event.getPackageName();
        if (!HomeButton.shouldOpen(false, pkg == null ? null : pkg.toString())) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastOpen < 700) return;
        lastOpen = now;
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onInterrupt() { }

    boolean weAreHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
    }
}
