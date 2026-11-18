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

    // История правок: прежний текст сообщения при каждой правке.
    private static final int EDIT_VERSIONS_LIMIT = 20;
    private static final int EDIT_TEXT_LIMIT = 4000;

    private static SharedPreferences editPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mogram_edits", android.content.Context.MODE_PRIVATE);
    }

    public static synchronized void addEdit(long dialogId, int id, String oldText, int date) {
        if (oldText == null || oldText.isEmpty()) {
            return;
        }
        try {
            String key = dialogId + ":" + id;
            org.json.JSONArray array = new org.json.JSONArray(editPrefs().getString(key, "[]"));
            if (array.length() > 0 && oldText.equals(array.getJSONObject(array.length() - 1).optString("t"))) {
                return;
            }
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("t", oldText.length() > EDIT_TEXT_LIMIT ? oldText.substring(0, EDIT_TEXT_LIMIT) : oldText);
            item.put("d", date);
            array.put(item);
            org.json.JSONArray trimmed = new org.json.JSONArray();
            for (int i = Math.max(0, array.length() - EDIT_VERSIONS_LIMIT); i < array.length(); ++i) {
                trimmed.put(array.get(i));
            }
            editPrefs().edit().putString(key, trimmed.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static synchronized boolean hasEdits(long dialogId, int id) {
        return editPrefs().contains(dialogId + ":" + id);
    }

    public static synchronized String formatEdits(long dialogId, int id) {
        try {
            org.json.JSONArray array = new org.json.JSONArray(editPrefs().getString(dialogId + ":" + id, "[]"));
            StringBuilder result = new StringBuilder();
            for (int i = array.length() - 1; i >= 0; --i) {
                org.json.JSONObject item = array.getJSONObject(i);
                int date = item.optInt("d");
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                if (date > 0) {
                    result.append(LocaleController.getInstance().getFormatterDayMonth().format(new java.util.Date(date * 1000L)))
                            .append(", ")
                            .append(LocaleController.getInstance().getFormatterDay().format(new java.util.Date(date * 1000L)))
                            .append("\n");
                }
                result.append(item.optString("t"));
            }
            return result.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    // Быстрые команды: строки вида "/имя = текст", команда целиком заменяется текстом при отправке.
    private static final String QUICK = "mogram_quick_commands";

    public static String getQuickCommandsText() {
        return prefs().getString(QUICK, "");
    }

    public static void setQuickCommandsText(String value) {
        prefs().edit().putString(QUICK, value == null ? "" : value).apply();
    }

    public static int quickCommandsCount() {
        int count = 0;
        for (String line : getQuickCommandsText().split("\n")) {
            if (line.trim().startsWith("/") && line.indexOf('=') > 0) {
                count++;
            }
        }
        return count;
    }

    // Возвращает текст команды, если сообщение целиком совпало с командой, иначе null.
    public static String expandQuickCommand(String message) {
        if (message == null) {
            return null;
        }
        String wanted = message.trim();
        if (!wanted.startsWith("/") || wanted.length() < 2 || wanted.indexOf('\n') >= 0) {
            return null;
        }
        for (String line : getQuickCommandsText().split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = line.substring(0, eq).trim();
            String text = line.substring(eq + 1).trim();
            if (!text.isEmpty() && name.equalsIgnoreCase(wanted)) {
                return text;
            }
        }
        return null;
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
