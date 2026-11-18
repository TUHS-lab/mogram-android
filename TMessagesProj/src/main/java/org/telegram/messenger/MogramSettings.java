package org.telegram.messenger;

import android.content.SharedPreferences;

// Mogram: настройки Mogram, общие для всех аккаунтов.
public class MogramSettings {

    private static final String GHOST = "mogram_ghost_mode";
    private static final String STREAMER = "mogram_streamer_mode";

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    // Режим призрака: не отмечать прочитанным, не показывать "в сети", не слать "печатает".
    public static boolean isGhostMode() {
        return prefs().getBoolean(GHOST, false);
    }

    public static void setGhostMode(boolean value) {
        prefs().edit().putBoolean(GHOST, value).apply();
    }

    // Режим стримера: свой номер телефона скрыт во всём интерфейсе.
    public static boolean isStreamerMode() {
        return prefs().getBoolean(STREAMER, false);
    }

    public static void setStreamerMode(boolean value) {
        prefs().edit().putBoolean(STREAMER, value).apply();
    }

    private static String digits(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                result.append(c);
            }
        }
        return result.toString();
    }

    // Принадлежит ли номер одному из аккаунтов, в которые выполнен вход.
    public static boolean isOwnPhone(String phone) {
        String wanted = digits(phone);
        if (wanted.isEmpty()) {
            return false;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
            UserConfig config = UserConfig.getInstance(a);
            if (config != null && config.isClientActivated() && config.getCurrentUser() != null
                    && wanted.equals(digits(config.getCurrentUser().phone))) {
                return true;
            }
        }
        return false;
    }

    public static String maskPhone(String phone) {
        int count = Math.max(digits(phone).length(), 6);
        StringBuilder result = new StringBuilder("+");
        for (int i = 0; i < count; ++i) {
            result.append('•');
        }
        return result.toString();
    }
}
