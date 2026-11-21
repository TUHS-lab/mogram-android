package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// Mogram: настройки Mogram, общие для всех аккаунтов.
public class MogramSettings {

    private static final String GHOST = "mogram_ghost_mode";
    private static final String PROFILE_ID = "mogram_profile_id";
    private static final String GHOST_EXCEPT = "mogram_ghost_except";
    private static final String STREAMER = "mogram_streamer_mode";
    private static final String KEEP_DELETED = "mogram_keep_deleted";
    private static final String COMMANDS = "mogram_chat_commands";
    private static final String ROUND_FOCUS = "mogram_round_focus";

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
            marks.add(MogramLogic.deletedKey(dialogId, ids.get(i)));
        }
        prefs().edit().putStringSet(DELETED_SET, new HashSet<>(marks)).apply();
    }

    public static synchronized boolean isMarkedDeleted(long dialogId, int id) {
        Set<String> marks = deletedMarks();
        return !marks.isEmpty() && (marks.contains(MogramLogic.deletedKey(dialogId, id)) || marks.contains(MogramLogic.deletedKey(0, id)));
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
        return MogramLogic.quickCommandsCount(getQuickCommandsText());
    }

    // Возвращает текст команды, если сообщение целиком совпало с командой, иначе null.
    public static String expandQuickCommand(String message) {
        return MogramLogic.expandQuickCommand(getQuickCommandsText(), message);
    }

    // Ложный пароль: при вводе тихо выходит из всех аккаунтов на этом устройстве.
    private static final String DECOY = "mogram_decoy_hash";

    private static String decoyHash(String code) {
        byte[] bytes = ("mogram-decoy:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
    }

    public static boolean hasDecoy() {
        return !prefs().getString(DECOY, "").isEmpty();
    }

    public static void setDecoy(String code) {
        prefs().edit().putString(DECOY, code == null || code.isEmpty() ? "" : decoyHash(code)).apply();
    }

    public static boolean isDecoyPasscode(String code) {
        String saved = prefs().getString(DECOY, "");
        return !saved.isEmpty() && code != null && saved.equals(decoyHash(code));
    }

    public static void runDecoy() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                MessagesController.getInstance(a).performLogout(1);
            }
        }
    }

    // Пока играет кружок, остальной экран чата размыт (Android 12 и новее).
    public static boolean roundFocus() {
        return prefs().getBoolean(ROUND_FOCUS, false);
    }

    public static void setRoundFocus(boolean value) {
        prefs().edit().putBoolean(ROUND_FOCUS, value).apply();
    }

    // Команды в чате (.id .del .pin .unpin .mute .unmute): выполняются вместо отправки сообщения.
    public static boolean commandsEnabled() {
        return prefs().getBoolean(COMMANDS, false);
    }

    public static void setCommandsEnabled(boolean value) {
        prefs().edit().putBoolean(COMMANDS, value).apply();
    }

    // Чаты-исключения: в них режим призрака не действует (прочтение и набор уходят как обычно).
    public static boolean isGhostExempt(long dialogId) {
        return MogramLogic.idListContains(prefs().getString(GHOST_EXCEPT, ""), dialogId);
    }

    // Переключает исключение, возвращает true, если чат теперь в исключениях.
    public static boolean toggleGhostExempt(long dialogId) {
        final String updated = MogramLogic.idListToggle(prefs().getString(GHOST_EXCEPT, ""), dialogId);
        prefs().edit().putString(GHOST_EXCEPT, updated).apply();
        return MogramLogic.idListContains(updated, dialogId);
    }

    private static long ghostDialogId(org.telegram.tgnet.TLObject object) {
        if (object instanceof org.telegram.tgnet.TLRPC.TL_messages_readHistory) {
            return DialogObject.getPeerDialogId(((org.telegram.tgnet.TLRPC.TL_messages_readHistory) object).peer);
        } else if (object instanceof org.telegram.tgnet.TLRPC.TL_channels_readHistory) {
            final org.telegram.tgnet.TLRPC.InputChannel channel = ((org.telegram.tgnet.TLRPC.TL_channels_readHistory) object).channel;
            return channel != null ? -channel.channel_id : 0;
        } else if (object instanceof org.telegram.tgnet.TLRPC.TL_messages_setTyping) {
            return DialogObject.getPeerDialogId(((org.telegram.tgnet.TLRPC.TL_messages_setTyping) object).peer);
        }
        return 0;
    }

    // Раздельные переключатели призрака (действуют, когда режим включён). По умолчанию всё скрыто.
    public static boolean ghostHides(String what) {
        return prefs().getBoolean("mogram_ghost_" + what, true);
    }

    public static void setGhostHides(String what, boolean value) {
        prefs().edit().putBoolean("mogram_ghost_" + what, value).apply();
    }

    // Режим призрака на уровне сети: запросы, которые выдают прочтение, просмотр историй или набор текста,
    // не уходят на сервер; запрос "в сети" превращается в "не в сети".
    public static boolean isGhostBlocked(org.telegram.tgnet.TLObject object) {
        final long dialogId = ghostDialogId(object);
        if (dialogId != 0 && isGhostExempt(dialogId)) {
            return false;
        }
        if (object instanceof org.telegram.tgnet.TLRPC.TL_messages_readHistory
                || object instanceof org.telegram.tgnet.TLRPC.TL_channels_readHistory
                || object instanceof org.telegram.tgnet.TLRPC.TL_messages_readMessageContents) {
            return ghostHides("read");
        } else if (object instanceof org.telegram.tgnet.TLRPC.TL_messages_setTyping) {
            return ghostHides("typing");
        }
        return (object instanceof org.telegram.tgnet.tl.TL_stories.TL_stories_readStories
                || object instanceof org.telegram.tgnet.tl.TL_stories.TL_stories_incrementStoryViews) && ghostHides("stories");
    }

    // Показывать ID и примерную дату регистрации в профиле. Включено по умолчанию.
    public static boolean showProfileId() {
        return prefs().getBoolean(PROFILE_ID, true);
    }

    public static void setShowProfileId(boolean value) {
        prefs().edit().putBoolean(PROFILE_ID, value).apply();
    }

    // Режим стримера: свой номер телефона скрыт во всём интерфейсе.
    public static boolean isStreamerMode() {
        return prefs().getBoolean(STREAMER, false);
    }

    public static void setStreamerMode(boolean value) {
        prefs().edit().putBoolean(STREAMER, value).apply();
    }

    private static String digits(String value) {
        return MogramLogic.digits(value);
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
        return MogramLogic.maskPhone(phone);
    }
}
