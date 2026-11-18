package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// Mogram: настройки Mogram, общие для всех аккаунтов.
public class MogramSettings {

    private static final String GHOST = "mogram_ghost_mode";
    private static final String STREAMER = "mogram_streamer_mode";
    private static final String KEEP_DELETED = "mogram_keep_deleted";

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

    // Не удалять у себя сообщения, которые собеседник удалил у всех.
    public static boolean keepDeleted() {
        return prefs().getBoolean(KEEP_DELETED, false);
    }

    public static void setKeepDeleted(boolean value) {
        prefs().edit().putBoolean(KEEP_DELETED, value).apply();
    }

    // Сообщения, которые собеседник удалил, а мы оставили: храним их ключи.
    private static final String DELETED_SET = "mogram_deleted_marks";
    private static final int DELETED_LIMIT = 5000;
    private static Set<String> deletedMarks;

    private static synchronized Set<String> deletedMarks() {
        if (deletedMarks == null) {
            deletedMarks = new HashSet<>(prefs().getStringSet(DELETED_SET, new HashSet<>()));
        }
        return deletedMarks;
    }

    // dialogId == 0: удаление пришло без диалога (личные чаты и обычные группы).
    public static synchronized void markDeleted(long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<String> marks = deletedMarks();
        if (marks.size() > DELETED_LIMIT) {
            marks.clear();
        }
        for (int i = 0; i < ids.size(); ++i) {
            marks.add(dialogId + ":" + ids.get(i));
        }
        prefs().edit().putStringSet(DELETED_SET, new HashSet<>(marks)).apply();
    }

    public static synchronized boolean isMarkedDeleted(long dialogId, int id) {
        Set<String> marks = deletedMarks();
        return !marks.isEmpty() && (marks.contains(dialogId + ":" + id) || marks.contains("0:" + id));
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
