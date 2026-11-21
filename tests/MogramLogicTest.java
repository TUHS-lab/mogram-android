import org.telegram.messenger.MogramLogic;
import org.telegram.messenger.MogramScam;

// Тесты чистой логики Mogram без JUnit: запуск `java MogramLogicTest`, при ошибке код выхода 1.
public class MogramLogicTest {

    private static int checks = 0;
    private static int failed = 0;

    private static void eq(String name, Object expected, Object actual) {
        checks++;
        final boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) {
            failed++;
            System.out.println("FAIL " + name + ": ожидалось <" + expected + ">, получено <" + actual + ">");
        }
    }

    private static String hex(int color) {
        return String.format("%08x", color);
    }

    public static void main(String[] args) {
        // digits
        eq("digits обычный", "380501234567", MogramLogic.digits("+38 (050) 123-45-67"));
        eq("digits пусто", "", MogramLogic.digits(""));
        eq("digits null", "", MogramLogic.digits(null));

        // maskPhone
        eq("mask длина", 13, MogramLogic.maskPhone("+380501234567").length());
        eq("mask без цифр", 7, MogramLogic.maskPhone("abc").length());
        eq("mask знак", '•', MogramLogic.maskPhone("+7999").charAt(1));
        eq("mask плюс", '+', MogramLogic.maskPhone("123456789").charAt(0));

        // grayColor
        final int blue = MogramLogic.grayColor(0xFF0A84FF);
        eq("gray синий равенство каналов", true,
                ((blue >> 16) & 255) == ((blue >> 8) & 255) && ((blue >> 8) & 255) == (blue & 255));
        eq("gray сохраняет альфу", 0x80, (MogramLogic.grayColor(0x800A84FF) >>> 24));
        eq("gray красный не трогает", 0xFFFF3B30, MogramLogic.grayColor(0xFFFF3B30));
        eq("gray зелёный не трогает", 0xFF34C759, MogramLogic.grayColor(0xFF34C759));
        eq("gray белый не трогает", 0xFFFFFFFF, MogramLogic.grayColor(0xFFFFFFFF));
        eq("gray серый не трогает", 0xFF1C1C1C, MogramLogic.grayColor(0xFF1C1C1C));
        eq("gray светлоголубой", true,
                ((MogramLogic.grayColor(0xFFDCEBFF) >> 16) & 255) == (MogramLogic.grayColor(0xFFDCEBFF) & 255));
        eq("gray яркость синего", true, (blue & 255) > 80 && (blue & 255) < 140);

        // quickCommandsCount
        final String commands = "/hi = Привет!\n/bye=Пока\nмусор\n  /sp  =  два слова  \n=нет имени\n/пусто =\n";
        eq("count", 4, MogramLogic.quickCommandsCount(commands));
        eq("count null", 0, MogramLogic.quickCommandsCount(null));

        // expandQuickCommand
        eq("expand точное", "Привет!", MogramLogic.expandQuickCommand(commands, "/hi"));
        eq("expand регистр", "Привет!", MogramLogic.expandQuickCommand(commands, "/HI"));
        eq("expand пробелы вокруг", "Привет!", MogramLogic.expandQuickCommand(commands, "  /hi  "));
        eq("expand без пробелов у =", "Пока", MogramLogic.expandQuickCommand(commands, "/bye"));
        eq("expand пробелы в тексте", "два слова", MogramLogic.expandQuickCommand(commands, "/sp"));
        eq("expand не команда", null, MogramLogic.expandQuickCommand(commands, "hi"));
        eq("expand неизвестная", null, MogramLogic.expandQuickCommand(commands, "/unknown"));
        eq("expand часть сообщения", null, MogramLogic.expandQuickCommand(commands, "/hi there"));
        eq("expand пустой текст команды", null, MogramLogic.expandQuickCommand(commands, "/пусто"));
        eq("expand только слеш", null, MogramLogic.expandQuickCommand(commands, "/"));
        eq("expand многострочное", null, MogramLogic.expandQuickCommand(commands, "/hi\nещё"));
        eq("expand null", null, MogramLogic.expandQuickCommand(commands, null));
        eq("expand список null", null, MogramLogic.expandQuickCommand(null, "/hi"));

        // chatCommandName
        eq("cmd id", ".id", MogramLogic.chatCommandName(".id"));
        eq("cmd регистр", ".pin", MogramLogic.chatCommandName("  .PIN "));
        eq("cmd mute", ".mute", MogramLogic.chatCommandName(".mute"));
        eq("cmd unmute", ".unmute", MogramLogic.chatCommandName(".unmute"));
        eq("cmd unpin", ".unpin", MogramLogic.chatCommandName(".unpin"));
        eq("cmd del", ".del", MogramLogic.chatCommandName(".del"));
        eq("cmd ghost", ".ghost", MogramLogic.chatCommandName(".ghost"));
        // сниппеты ;имя
        final String snip = "/hi = Привет\n;sig = С уважением, Тест\n;a_1 = один";
        eq("snip count", 3, MogramLogic.quickCommandsCount(snip));
        eq("snip в тексте", "Спасибо. С уважением, Тест", MogramLogic.expandSnippets(snip, "Спасибо. ;sig"));
        eq("snip в начале", "один и два", MogramLogic.expandSnippets(snip, ";a_1 и два"));
        eq("snip регистр", "один", MogramLogic.expandSnippets(snip, ";A_1"));
        eq("snip неизвестное", "до ;nope", MogramLogic.expandSnippets(snip, "до ;nope"));
        eq("snip без пробела перед", "а;sig", MogramLogic.expandSnippets(snip, "а;sig"));
        eq("snip пустой список", "x ;sig", MogramLogic.expandSnippets("", "x ;sig"));
        eq("snip null", null, MogramLogic.expandSnippets(snip, null));
        eq("snip два", "один один", MogramLogic.expandSnippets(snip, ";a_1 ;a_1"));
        // скам
        eq("scam host", "example.com", MogramScam.hostOf("https://www.Example.com/path"));
        eq("scam host без схемы", "t.me", MogramScam.hostOf("t.me/abc"));
        eq("scam подмена", true, MogramScam.linkReason("fragment.com", "https://fragment-login.ru/x") != null);
        eq("scam настоящая", null, MogramScam.linkReason("fragment.com", "https://fragment.com/x"));
        eq("scam подмена текста", true, MogramScam.linkReason("https://bank.com", "https://evil.example/") != null);
        eq("scam обычный текст", null, MogramScam.linkReason("нажми сюда", "https://example.com"));
        eq("scam поддомен", null, MogramScam.linkReason("example.com", "https://login.example.com/"));
        eq("scam сервис в тексте", true, MogramScam.linkReason("Open Tonkeeper", "https://tonkeeper-login.xyz/") != null);
        eq("scam гарант", true, MogramScam.isGarantMention("пишите @super_garant_bot"));
        eq("scam нет гаранта", false, MogramScam.isGarantMention("привет как дела"));
        eq("scam покупатель", true, MogramScam.isBuyerBait("Do you still sell your @cooluser username?"));
        eq("scam не покупатель", false, MogramScam.isBuyerBait("привет, как дела"));
        eq("scam тревога две", true, MogramScam.isAlarmText("Your account has been blocked. Pass verification: unlock your account"));
        eq("scam тревога одна", false, MogramScam.isAlarmText("пройдите верификацию в банке"));
        // idListContains / idListToggle
        eq("list пусто", false, MogramLogic.idListContains("", 5));
        eq("list null", false, MogramLogic.idListContains(null, 5));
        eq("list есть", true, MogramLogic.idListContains("1,5,-7", 5));
        eq("list отрицательное", true, MogramLogic.idListContains("1,5,-7", -7));
        eq("list не подстрока", false, MogramLogic.idListContains("15", 5));
        eq("toggle добавить", "1,5", MogramLogic.idListToggle("1", 5));
        eq("toggle в пустой", "5", MogramLogic.idListToggle("", 5));
        eq("toggle убрать", "1,-7", MogramLogic.idListToggle("1,5,-7", 5));
        eq("toggle убрать последний", "", MogramLogic.idListToggle("5", 5));
        eq("toggle дважды", "1", MogramLogic.idListToggle(MogramLogic.idListToggle("1", 5), 5));
        eq("cmd неизвестная", null, MogramLogic.chatCommandName(".unknown"));
        eq("cmd с аргументом", null, MogramLogic.chatCommandName(".del 5"));
        eq("cmd обычный текст", null, MogramLogic.chatCommandName("привет"));
        eq("cmd многоточие", null, MogramLogic.chatCommandName("..."));
        eq("cmd точка", null, MogramLogic.chatCommandName("."));
        eq("cmd null", null, MogramLogic.chatCommandName(null));

        // deletedKey
        eq("key", "-100123:45", MogramLogic.deletedKey(-100123L, 45));
        eq("key личный", "0:7", MogramLogic.deletedKey(0, 7));


        // registrationEstimate и registrationText
        eq("reg опорная точка", 1382662080L, MogramLogic.registrationEstimate(217509L)[0]);
        eq("reg опорная точка граница", 0L, MogramLogic.registrationEstimate(217509L)[1]);
        eq("reg раньше первой", -1L, MogramLogic.registrationEstimate(1L)[1]);
        eq("reg раньше первой время", 1382662080L, MogramLogic.registrationEstimate(1L)[0]);
        eq("reg позже последней", 1L, MogramLogic.registrationEstimate(9_000_000_000L)[1]);
        eq("reg позже последней время", 1764543600L, MogramLogic.registrationEstimate(9_000_000_000L)[0]);
        final long mid = MogramLogic.registrationEstimate(1_000_000_000L)[0];
        eq("reg интерполяция между точками", true, mid > 1574693313L && mid < 1576957200L);
        long previous = 0;
        boolean monotonic = true;
        for (long id = 217509L; id < 8_587_904_527L; id += 7_321_123L) {
            final long t = MogramLogic.registrationEstimate(id)[0];
            if (t < previous) {
                monotonic = false;
            }
            previous = t;
        }
        eq("reg не убывает с ростом номера", true, monotonic);
        eq("reg текст опорной точки", "≈ 2013-10", MogramLogic.registrationText(217509L));
        eq("reg текст до первой", "≤ 2013-10", MogramLogic.registrationText(100L));
        eq("reg текст после последней", "≥ 2025-11", MogramLogic.registrationText(9_999_999_999L));
        eq("reg текст нуль", "", MogramLogic.registrationText(0));
        eq("reg текст отрицательный", "", MogramLogic.registrationText(-5));
        eq("reg текст формат", true, MogramLogic.registrationText(2_000_000_000L).matches("≈ 20[0-9][0-9]-[0-9][0-9]"));

        System.out.println("Проверок: " + checks + ", ошибок: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
