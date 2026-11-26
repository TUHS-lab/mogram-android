package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;

import org.telegram.ui.ActionBar.AlertDialog;

// Mogram: перенос настроек и плагинов из установленного exteraGram. Android не даёт одному приложению
// читать данные другого, поэтому перенос идёт файлами: exteraGram выгружает, Mogram открывает.
public final class MogramImport {

    private static final String[] PACKAGES = {"com.exteragram.messenger", "com.exteragram.messenger.beta"};

    private MogramImport() {
    }

    private static Intent launchIntent(Activity activity) {
        for (String name : PACKAGES) {
            final Intent intent = activity.getPackageManager().getLaunchIntentForPackage(name);
            if (intent != null) {
                return intent;
            }
        }
        return null;
    }

    // Один раз после входа, только если exteraGram установлен.
    public static void offerOnce(Activity activity) {
        if (activity == null || MogramSettings.prefs().getBoolean("mogram_import_asked", false)) {
            return;
        }
        final Intent intent = launchIntent(activity);
        if (intent == null) {
            return;
        }
        MogramSettings.prefs().edit().putBoolean("mogram_import_asked", true).apply();
        show(activity, intent);
    }

    private static void show(Activity activity, Intent intent) {
        final boolean ru = "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage());
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(ru ? "Перенести из exteraGram?" : "Import from exteraGram?");
        builder.setMessage(ru
                ? "Настройки: в exteraGram откройте Настройки, Другое, «Экспорт настроек» и отправьте файл .extera в Mogram.\n\nПлагины: отправьте каждый файл .plugin из exteraGram в Mogram и нажмите на него, чтобы установить.\n\nАвтоматически забрать их нельзя: Android не даёт читать данные другого приложения."
                : "Settings: in exteraGram open Settings, Other, Export settings and send the .extera file to Mogram.\n\nPlugins: send each .plugin file from exteraGram to Mogram and tap it to install.\n\nThey cannot be taken automatically: Android does not let one app read another app's data.");
        builder.setPositiveButton(ru ? "Открыть exteraGram" : "Open exteraGram", (dialog, which) -> {
            try {
                activity.startActivity(intent);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
        builder.setNegativeButton(ru ? "Позже" : "Later", null);
        builder.show();
    }
}
