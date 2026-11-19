package org.telegram.messenger;

import java.util.Locale;

// Mogram: чистая логика без зависимостей от Android, чтобы её можно было проверять обычными тестами
// (см. tests/MogramLogicTest.java). Настройки, интерфейс и сеть живут в других классах.
public final class MogramLogic {

    private MogramLogic() {
    }

    // Только цифры из строки.
    public static String digits(String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            final char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                result.append(c);
            }
        }
        return result.toString();
    }

    // Номер, скрытый точками: столько же знаков, но не меньше шести.
    public static String maskPhone(String phone) {
        final int count = Math.max(digits(phone).length(), 6);
        final StringBuilder result = new StringBuilder("+");
        for (int i = 0; i < count; ++i) {
            result.append('•');
        }
        return result.toString();
    }

    // Синие и фиолетовые цвета (b заметно больше r и не меньше g) становятся серыми той же яркости.
    public static int grayColor(int color) {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        if (b - r > 24 && b >= g) {
            final int gray = Math.min(255, (int) ((0.299f * r + 0.587f * g + 0.114f * b) * 0.9f + 0.5f));
            return (color & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
        }
        return color;
    }

    // Сколько строк вида "/имя = текст" в списке быстрых команд.
    public static int quickCommandsCount(String commands) {
        if (commands == null) {
            return 0;
        }
        int count = 0;
        for (String line : commands.split("\n")) {
            if (line.trim().startsWith("/") && line.indexOf('=') > 0) {
                count++;
            }
        }
        return count;
    }

    // Текст команды, если сообщение целиком совпало с командой, иначе null.
    public static String expandQuickCommand(String commands, String message) {
        if (commands == null || message == null) {
            return null;
        }
        final String wanted = message.trim();
        if (!wanted.startsWith("/") || wanted.length() < 2 || wanted.indexOf('\n') >= 0) {
            return null;
        }
        for (String line : commands.split("\n")) {
            final int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            final String name = line.substring(0, eq).trim();
            final String text = line.substring(eq + 1).trim();
            if (!text.isEmpty() && name.equalsIgnoreCase(wanted)) {
                return text;
            }
        }
        return null;
    }

    // Имя команды чата (".id", ".pin" и т. д.) в нижнем регистре, либо null, если это не команда.
    public static String chatCommandName(String message) {
        if (message == null) {
            return null;
        }
        final String text = message.trim().toLowerCase(Locale.ROOT);
        if (text.length() < 2 || !text.startsWith(".") || text.indexOf(' ') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        switch (text) {
            case ".id":
            case ".mute":
            case ".unmute":
            case ".pin":
            case ".unpin":
            case ".del":
                return text;
            default:
                return null;
        }
    }

    // Ключ пометки "удалено": диалог и номер сообщения.
    public static String deletedKey(long dialogId, int id) {
        return dialogId + ":" + id;
    }
}
