package org.telegram.messenger;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

// Mogram: защита от скама. Чистая логика без Android, проверяется обычными тестами.
public class MogramScam {

    private static final Pattern LOOKS_LIKE_URL = Pattern.compile("^(https?://)?([\\w-]+\\.)+[a-z]{2,}(/\\S*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GARANT = Pattern.compile("(@\\w*(garant|гарант|escrow|guarant)\\w*|t\\.me/\\w*(garant|escrow|guarant)\\w*|(playerok|funpay|plati|fanpay|player0k)\\W{0,3}(гарант|garant|escrow))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BUYER_PHRASE = Pattern.compile("(do you (still )?sell|are you (still )?selling|selling your|want to buy your|wanna buy your|interested in (buying|your)|buy your|продаёшь|продаешь|продаете|продаёте|куплю (твой|ваш)|выкуплю|интересует (твой|ваш)|sell (your |the |this )?user ?name|(offer|price) for your|your user ?name|готов продать|готовы продать|сколько хочешь за|за сколько продашь)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BUYER_SUBJECT = Pattern.compile("(@\\w{4,}|user ?name|nft|gift|юзернейм|юзер|подарок|нфт)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALARM = Pattern.compile("(profile (has been|is|was) (blocked|restricted|frozen|suspended|limited)|account (has been|is|was) (blocked|restricted|frozen|suspended|limited)|unlock (your )?(profile|account|wallet)|(profile|account|wallet|identity) verification|account will be (deleted|removed|closed|banned)|assets? will be (burned|lost|frozen|deleted)|deposit error|pending limits|разблокир\\w+ (профиль|аккаунт|кошел)|пройдите верификацию|подтвердите (личность|профиль)|активы будут (сожжены|утеряны|заморожены))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String[] SERVICES = {"fragment", "getgems", "portals", "tonkeeper", "playerok", "funpay"};
    private static final String[] SERVICE_HOSTS = {"fragment.com", "getgems.io", "tonkeeper.com", "playerok.com", "funpay.com"};

    public static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        try {
            final String host = new URI(value).getHost();
            if (host == null) {
                return "";
            }
            final String lower = host.toLowerCase(Locale.ROOT);
            return lower.startsWith("www.") ? lower.substring(4) : lower;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isGarantMention(String text) {
        return text != null && GARANT.matcher(text).find();
    }

    public static boolean isBuyerBait(String text) {
        return text != null && BUYER_PHRASE.matcher(text).find() && BUYER_SUBJECT.matcher(text).find();
    }

    // Нужно две разные фразы тревоги: одной мало, в обычной переписке тоже бывает "верификация".
    public static boolean isAlarmText(String text) {
        if (text == null) {
            return false;
        }
        final java.util.regex.Matcher matcher = ALARM.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (++count >= 2) {
                return true;
            }
        }
        return false;
    }

    // Похоже ли сообщение на мошенничество: покупатель юзернейма/подарка или сообщение о "блокировке" с просьбой верификации.
    public static boolean looksLikeScam(String text) {
        return isBuyerBait(text) || isAlarmText(text);
    }

    // Почему ссылке не стоит доверять, либо null, если всё в порядке.
    public static String linkReason(String shown, String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        final String host = hostOf(url);
        final String text = shown == null ? "" : shown.trim();
        if (!text.isEmpty() && LOOKS_LIKE_URL.matcher(text).matches()) {
            final String shownHost = hostOf(text);
            if (!shownHost.isEmpty() && !host.equals(shownHost) && !host.endsWith("." + shownHost) && !shownHost.endsWith("." + host)) {
                return "Link text shows " + shownHost + ", but it opens " + (host.isEmpty() ? url : host) + ".";
            }
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        for (String name : SERVICES) {
            if (!text.isEmpty() && lower.contains(name)) {
                boolean real = false;
                for (String good : SERVICE_HOSTS) {
                    if (host.endsWith(good)) {
                        real = true;
                        break;
                    }
                }
                if (!real) {
                    return "The link pretends to be a known service, but opens " + (host.isEmpty() ? url : host) + ".";
                }
                break;
            }
        }
        if (isGarantMention(url)) {
            return "This is an escrow bot. Real marketplaces do not run deals through chat bots.";
        }
        return null;
    }
}
