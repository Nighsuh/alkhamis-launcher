package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** Opens the system installer for an APK that arrived in Download/AlkhamisTransfer. */
final class TransferInstall {
    private TransferInstall() {}

    static void prompt(Context ctx, String name) {
        TransferFiles.Item item = TransferFiles.open(ctx).find(name);
        if (item == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(settings);
            } catch (Exception ignored) { }
            return;
        }
        Uri uri = TransferFiles.share(ctx, item);
        if (uri == null) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (Exception ignored) { }
    }
}
