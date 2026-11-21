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
            case ".ghost":
                return text;
            default:
                return null;
        }
    }

    // Список чисел в одной строке через запятую: есть ли в нём число.
    public static boolean idListContains(String list, long id) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (String part : list.split(",")) {
            if (part.trim().equals(Long.toString(id))) {
                return true;
            }
        }
        return false;
    }

    // Добавить число в список, а если оно уже есть, убрать. Порядок остальных сохраняется.
    public static String idListToggle(String list, long id) {
        final StringBuilder sb = new StringBuilder();
        boolean found = false;
        if (list != null && !list.isEmpty()) {
            for (String part : list.split(",")) {
                final String item = part.trim();
                if (item.isEmpty()) {
                    continue;
                }
                if (item.equals(Long.toString(id))) {
                    found = true;
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(item);
            }
        }
        if (!found) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    // Ключ пометки "удалено": диалог и номер сообщения.
    public static String deletedKey(long dialogId, int id) {
        return dialogId + ":" + id;
    }

    // Оценка даты регистрации по номеру аккаунта: опорные точки "номер -> время" с линейной
    // интерполяцией между ними (та же таблица, что и в десктопном Mogram), без сети.
    private static final long[] ANCHOR_IDS = {
            217509L, 2768400L, 2768409L, 6256671L, 11538510L, 11538514L,
            15835240L, 15835244L, 23646070L, 23646077L, 38015510L, 44634660L,
            45641754L, 46145305L, 54845230L, 60457419L, 63263518L, 72776425L,
            95308423L, 103204956L, 110306836L, 115831565L, 124872440L, 124872445L,
            127929226L, 133909600L, 133909606L, 139646974L, 143445120L, 143445125L,
            155983984L, 172144707L, 188459175L, 222021230L, 222021233L, 225034350L,
            225034354L, 267103082L, 279333717L, 281973858L, 285253070L, 285253072L,
            294851030L, 294851037L, 297621220L, 297621225L, 310042805L, 328122828L,
            339965214L, 369669040L, 369669043L, 399597034L, 400169470L, 400169472L,
            409518943L, 484976017L, 501535268L, 554990133L, 573604341L, 601341900L,
            655730151L, 675879664L, 695531082L, 707751101L, 717887953L, 774723749L,
            820050353L, 871177218L, 920778234L, 970269006L, 1020178246L, 1040151903L,
            1059845356L, 1081554131L, 1130489378L, 1145856008L, 1170070589L, 1202891775L,
            1232810985L, 1279382482L, 1334421204L, 1382531194L, 1383895297L, 1415571066L,
            1439824385L, 1471194210L, 1485680298L, 1514538817L, 1578805533L, 1649902679L,
            1692464211L, 1735271901L, 1788462219L, 1824157837L, 1881430862L, 1938921569L,
            1991158335L, 2030606431L, 2250645697L, 5017595749L, 5035057026L, 5065900297L,
            5124099423L, 5199399786L, 5249255238L, 5290311052L, 5297354132L, 5318972588L,
            5362593868L, 5396575599L, 5402540458L, 5502182459L, 5562600534L, 5584365163L,
            5608562550L, 5644144970L, 5731896020L, 5778063231L, 5821105646L, 5844821188L,
            5856660622L, 5903848700L, 5983654523L, 6025903944L, 6115110768L, 6297860552L,
            6373693377L, 6433264186L, 6507968649L, 6609923725L, 6639644540L, 6845043276L,
            7043862629L, 7129689557L, 7191651807L, 7255466649L, 7347120414L, 7424559477L,
            7490725809L, 7509628470L, 7519637470L, 7537361651L, 7552093513L, 7716779743L,
            7967849270L, 8096580061L, 8218310808L, 8379482677L, 8541347308L, 8587904527L
    };
    private static final long[] ANCHOR_DATES = {
            1382662080L, 1383242400L, 1383264000L, 1390013486L, 1391104800L, 1391212000L,
            1392832800L, 1392940000L, 1393351200L, 1393459000L, 1393632000L, 1396720800L,
            1398167867L, 1400198000L, 1403287200L, 1408075000L, 1414454000L, 1427835600L,
            1428238000L, 1433032767L, 1434314267L, 1438524943L, 1439838000L, 1439856000L,
            1442623033L, 1444158000L, 1444176000L, 1444273680L, 1448910000L, 1448928000L,
            1449370973L, 1454045640L, 1457850160L, 1465326000L, 1465344000L, 1466190000L,
            1466208000L, 1472677200L, 1473142920L, 1475301600L, 1476817200L, 1476835000L,
            1479582000L, 1479600000L, 1481828400L, 1481846000L, 1487106000L, 1487205960L,
            1489772244L, 1490814000L, 1490918000L, 1496257200L, 1501441200L, 1501459000L,
            1506848400L, 1516084200L, 1518638400L, 1521187200L, 1523822400L, 1531829829L,
            1537472400L, 1539510300L, 1541019600L, 1542304800L, 1545429600L, 1550821640L,
            1557302400L, 1561951200L, 1568803164L, 1574693313L, 1576957200L, 1577817000L,
            1578848886L, 1580504400L, 1583002800L, 1586342040L, 1588284000L, 1588310640L,
            1590890220L, 1590934320L, 1597360114L, 1600188120L, 1601492400L, 1604817900L,
            1606762800L, 1606813200L, 1609437600L, 1609441200L, 1612119600L, 1612670965L,
            1615402500L, 1618786994L, 1622458800L, 1622576984L, 1623440640L, 1626933294L,
            1630556060L, 1631992680L, 1633515812L, 1638309600L, 1640352847L, 1640977200L,
            1643662800L, 1645002097L, 1646383782L, 1647451401L, 1648760400L, 1651937341L,
            1652024520L, 1654553525L, 1656615600L, 1658314837L, 1659301200L, 1659366750L,
            1664012820L, 1664703130L, 1667242122L, 1667477640L, 1671677863L, 1672506000L,
            1673991930L, 1675280531L, 1681223063L, 1681680600L, 1683830609L, 1688115560L,
            1692301478L, 1692763697L, 1695301080L, 1696100400L, 1698760800L, 1704907301L,
            1711876800L, 1715623200L, 1721136600L, 1724214000L, 1724655600L, 1727733600L,
            1729065600L, 1732991400L, 1734202800L, 1735686000L, 1738224000L, 1739042100L,
            1743318900L, 1745982867L, 1758394800L, 1759605750L, 1763236800L, 1764543600L
    };

    // {время в секундах, граница}: 0 примерно, -1 не позже этого времени, 1 не раньше этого времени.
    public static long[] registrationEstimate(long id) {
        final int last = ANCHOR_IDS.length - 1;
        if (id < ANCHOR_IDS[0]) {
            return new long[]{ANCHOR_DATES[0], -1};
        } else if (id > ANCHOR_IDS[last]) {
            return new long[]{ANCHOR_DATES[last], 1};
        }
        int low = 0;
        int high = last;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (ANCHOR_IDS[mid] < id) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        if (ANCHOR_IDS[low] == id || low == 0) {
            return new long[]{ANCHOR_DATES[low], 0};
        }
        final double part = (double) (id - ANCHOR_IDS[low - 1]) / (double) (ANCHOR_IDS[low] - ANCHOR_IDS[low - 1]);
        return new long[]{(long) (ANCHOR_DATES[low - 1] + part * (ANCHOR_DATES[low] - ANCHOR_DATES[low - 1])), 0};
    }

    // Подпись вида "≈ 2024-01", "≤ 2013-10" (не позже), "≥ 2025-11" (не раньше). Месяц по UTC.
    public static String registrationText(long id) {
        if (id <= 0) {
            return "";
        }
        final long[] estimate = registrationEstimate(id);
        final java.time.ZonedDateTime time = java.time.Instant.ofEpochSecond(estimate[0]).atZone(java.time.ZoneOffset.UTC);
        final String month = String.format(Locale.ROOT, "%04d-%02d", time.getYear(), time.getMonthValue());
        return (estimate[1] < 0 ? "\u2264 " : estimate[1] > 0 ? "\u2265 " : "\u2248 ") + month;
    }
}
