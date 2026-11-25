package org.telegram.messenger;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

// Mogram Link: связь между устройствами через закрытый канал «Mogram Link» в вашем аккаунте.
// Протокол MGL1 (одинаков на ПК и телефоне):
//   текст сообщения = "⁣mgl1:" + base64url(iv[12] + AES-256-GCM(json)),
//   ключ = PBKDF2-HMAC-SHA1(пароль связи, соль "mgl1:" + id пользователя, 200000, 256 бит).
// Типы сообщений (поле t): clip (буфер обмена), now (что играет), act (в каком чате я сейчас),
// state (заметки, теги, локальные закрепы). Поле d = id устройства-отправителя.
public class MogramLink implements NotificationCenter.NotificationCenterDelegate {

    public static final String MARK = "⁣mgl1:";
    private static final String CHANNEL_TITLE = "Mogram Link";
    private static final long EVENT_TTL_MS = 90_000;

    private static final MogramLink instance = new MogramLink();

    public static MogramLink get() {
        return instance;
    }

    public interface Listener {
        void onNowPlaying(String device, String title, String artist, long soundcloudId);
    }

    private int account = -1;
    private long channelId;
    private boolean started;
    private boolean creating;
    private SecretKey key;
    private String keyFor = "";
    private final SecureRandom random = new SecureRandom();
    private ClipboardManager clipboard;
    private String lastClipHash = "";
    private long stateVersion;
    private int ownStateMessageId;
    private final HashMap<Long, Long> remoteActive = new HashMap<>();
    private long remoteActiveAt;
    public volatile String remoteTitle;
    public volatile String remoteArtist;
    public volatile String remoteDevice;
    public volatile long remoteSoundcloudId;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // --- настройки ---

    public static boolean enabled() {
        return MogramSettings.prefs().getBoolean("mogram_link", false);
    }

    public static boolean feature(String name) {
        return enabled() && MogramSettings.prefs().getBoolean("mogram_link_" + name, true);
    }

    public static String deviceId() {
        String id = MogramSettings.prefs().getString("mogram_link_device", "");
        if (id.isEmpty()) {
            final byte[] bytes = new byte[5];
            new SecureRandom().nextBytes(bytes);
            id = "a" + Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.URL_SAFE);
            MogramSettings.prefs().edit().putString("mogram_link_device", id).apply();
        }
        return id;
    }

    // --- запуск ---

    public void start(int currentAccount) {
        if (!enabled() || started) {
            return;
        }
        final String password = MogramSettings.prefs().getString("mogram_link_password", "");
        if (password.isEmpty()) {
            return;
        }
        account = currentAccount;
        started = true;
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingDidStart);
        AndroidUtilities.runOnUIThread(() -> {
            clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.addPrimaryClipChangedListener(this::onLocalClip);
            }
            findOrCreateChannel();
        });
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        channelId = 0;
    }

    private void findOrCreateChannel() {
        final MessagesController controller = MessagesController.getInstance(account);
        for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
            if (!DialogObject.isChatDialog(dialog.id)) {
                continue;
            }
            final TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (chat != null && chat.creator && !chat.megagroup && CHANNEL_TITLE.equals(chat.title)) {
                channelId = chat.id;
                onChannelReady();
                return;
            }
        }
        if (!creating) {
            creating = true;
            controller.createChat(CHANNEL_TITLE, new ArrayList<>(), "mgl1", ChatObject.CHAT_TYPE_CHANNEL, false, null, null, 0, null);
        }
    }

    private void onChannelReady() {
        loadHistory();
    }

    @Override
    public void didReceivedNotification(int id, int acc, Object... args) {
        if (acc != account) {
            return;
        }
        if (id == NotificationCenter.chatDidCreated) {
            if (creating && channelId == 0 && args != null && args.length > 0 && args[0] instanceof Long) {
                final TLRPC.Chat chat = MessagesController.getInstance(account).getChat((Long) args[0]);
                if (chat != null && CHANNEL_TITLE.equals(chat.title)) {
                    creating = false;
                    channelId = chat.id;
                    onChannelReady();
                }
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            if (channelId == 0 || args.length < 2 || !(args[0] instanceof Long) || (Long) args[0] != -channelId) {
                return;
            }
            @SuppressWarnings("unchecked") final ArrayList<MessageObject> list = (ArrayList<MessageObject>) args[1];
            for (MessageObject message : list) {
                handle(message);
            }
        } else if (id == NotificationCenter.messagePlayingDidStart) {
            publishNow();
        }
    }

    private void loadHistory() {
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(-channelId);
        req.limit = 40;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                for (int i = messages.messages.size() - 1; i >= 0; i--) {
                    final TLRPC.Message message = messages.messages.get(i);
                    final MessageObject object = new MessageObject(account, message, false, false);
                    AndroidUtilities.runOnUIThread(() -> handle(object));
                }
            }
        });
    }

    // --- шифрование ---

    private SecretKey key() throws Exception {
        final String password = MogramSettings.prefs().getString("mogram_link_password", "");
        final long user = UserConfig.getInstance(account).getClientUserId();
        final String id = password + "|" + user;
        if (key == null || !id.equals(keyFor)) {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            final byte[] raw = factory.generateSecret(new PBEKeySpec(password.toCharArray(), ("mgl1:" + user).getBytes(StandardCharsets.UTF_8), 200000, 256)).getEncoded();
            key = new SecretKeySpec(raw, "AES");
            keyFor = id;
        }
        return key;
    }

    private String seal(JSONObject json) throws Exception {
        final byte[] iv = new byte[12];
        random.nextBytes(iv);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        final byte[] ct = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
        final byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return MARK + Base64.encodeToString(all, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    private JSONObject open(String text) {
        try {
            if (text == null || !text.startsWith(MARK)) {
                return null;
            }
            final byte[] all = Base64.decode(text.substring(MARK.length()), Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new JSONObject(new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            return null;
        }
    }

    // --- отправка ---

    private void send(JSONObject json, boolean ephemeral, boolean replaceState) {
        if (channelId == 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                json.put("d", deviceId());
                json.put("ts", System.currentTimeMillis());
                final TLRPC.TL_messages_sendMessage req = new TLRPC.TL_messages_sendMessage();
                req.peer = MessagesController.getInstance(account).getInputPeer(-channelId);
                req.message = seal(json);
                req.silent = true;
                req.no_webpage = true;
                req.random_id = Utilities.random.nextLong();
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (error == null && response instanceof TLRPC.Updates) {
                        MessagesController.getInstance(account).processUpdates((TLRPC.Updates) response, false);
                    }
                });
            } catch (Throwable ignore) {
            }
        });
    }

    private void deleteLater(int messageId, long delay) {
        AndroidUtilities.runOnUIThread(() -> {
            final TLRPC.TL_channels_deleteMessages req = new TLRPC.TL_channels_deleteMessages();
            req.channel = MessagesController.getInstance(account).getInputChannel(channelId);
            req.id.add(messageId);
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            });
        }, delay);
    }

    // --- приём ---

    private void handle(MessageObject message) {
        if (message == null || message.messageOwner == null || message.messageOwner.message == null) {
            return;
        }
        final JSONObject json = open(message.messageOwner.message);
        if (json == null) {
            return;
        }
        final String type = json.optString("t");
        final String device = json.optString("d");
        if (device.equals(deviceId())) {
            if (!type.equals("state")) {
                deleteLater(message.getId(), EVENT_TTL_MS);
            } else {
                final int previous = ownStateMessageId;
                ownStateMessageId = message.getId();
                if (previous != 0 && previous != ownStateMessageId) {
                    deleteLater(previous, 1000);
                }
            }
            return;
        }
        final long ts = json.optLong("ts");
        final boolean fresh = System.currentTimeMillis() - ts < EVENT_TTL_MS;
        switch (type) {
            case "clip":
                if (fresh && feature("clip")) {
                    applyRemoteClip(json.optString("text"));
                }
                break;
            case "now":
                if (feature("music")) {
                    remoteDevice = device;
                    remoteTitle = json.optString("title");
                    remoteArtist = json.optString("artist");
                    remoteSoundcloudId = json.optLong("sc");
                    if (fresh && listener != null) {
                        listener.onNowPlaying(device, remoteTitle, remoteArtist, remoteSoundcloudId);
                    }
                }
                break;
            case "act":
                if (feature("activity")) {
                    synchronized (remoteActive) {
                        remoteActive.clear();
                        final long chat = json.optLong("chat");
                        if (chat != 0 && fresh) {
                            remoteActive.put(chat, ts);
                        }
                        remoteActiveAt = ts;
                    }
                }
                break;
            case "state":
                if (feature("notes")) {
                    applyState(json, ts);
                }
                break;
            default:
                break;
        }
    }

    // --- буфер обмена ---

    private void onLocalClip() {
        if (!feature("clip") || channelId == 0 || clipboard == null) {
            return;
        }
        try {
            final ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0 || data.getItemAt(0).getText() == null) {
                return;
            }
            final String text = data.getItemAt(0).getText().toString();
            if (text.isEmpty() || text.length() > 2000 || Integer.toString(text.hashCode()).equals(lastClipHash)) {
                return;
            }
            lastClipHash = Integer.toString(text.hashCode());
            final JSONObject json = new JSONObject();
            json.put("t", "clip");
            json.put("text", text);
            send(json, true, false);
        } catch (Throwable ignore) {
        }
    }

    private void applyRemoteClip(String text) {
        if (text == null || text.isEmpty() || clipboard == null) {
            return;
        }
        lastClipHash = Integer.toString(text.hashCode());
        AndroidUtilities.runOnUIThread(() -> clipboard.setPrimaryClip(ClipData.newPlainText("Mogram Link", text)));
    }

    // --- музыка ---

    private void publishNow() {
        if (!feature("music") || channelId == 0) {
            return;
        }
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        try {
            final JSONObject json = new JSONObject();
            json.put("t", "now");
            json.put("title", playing.getMusicTitle());
            json.put("artist", playing.getMusicAuthor());
            json.put("sc", 0);
            send(json, true, false);
        } catch (Throwable ignore) {
        }
    }

    // --- активность ---

    public void publishActive(long dialogId) {
        if (!feature("activity") || channelId == 0) {
            return;
        }
        try {
            final JSONObject json = new JSONObject();
            json.put("t", "act");
            json.put("chat", dialogId);
            send(json, true, false);
        } catch (Throwable ignore) {
        }
    }

    // Читают ли этот чат на другом устройстве прямо сейчас (последние 25 секунд).
    public boolean isActiveElsewhere(long dialogId) {
        if (!feature("activity")) {
            return false;
        }
        synchronized (remoteActive) {
            final Long ts = remoteActive.get(dialogId);
            return ts != null && System.currentTimeMillis() - ts < 25_000;
        }
    }

    // --- активный чат: пульс раз в 15 секунд, пока чат открыт ---

    private long activeDialog;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (activeDialog != 0) {
                publishActive(activeDialog);
                AndroidUtilities.runOnUIThread(this, 15_000);
            }
        }
    };

    public void setActiveDialog(long dialogId) {
        if (!started) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(heartbeat);
        activeDialog = dialogId;
        if (dialogId != 0) {
            heartbeat.run();
        } else {
            publishActive(0);
        }
    }

    // --- заметки, теги, локальные закрепы ---

    private final Runnable stateDebounce = this::publishState;

    // Вызывается при изменении заметки, тега или локального закрепа.
    public void onSharedChanged() {
        if (!started) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(stateDebounce);
        AndroidUtilities.runOnUIThread(stateDebounce, 3000);
    }

    public void publishState() {
        if (!feature("notes") || channelId == 0) {
            return;
        }
        try {
            final JSONObject json = MogramSettings.exportShared();
            json.put("t", "state");
            stateVersion = System.currentTimeMillis();
            MogramSettings.prefs().edit().putLong("mogram_link_state_ts", stateVersion).apply();
            send(json, false, true);
        } catch (Throwable ignore) {
        }
    }

    private void applyState(JSONObject json, long ts) {
        if (ts <= MogramSettings.prefs().getLong("mogram_link_state_ts", 0)) {
            return;
        }
        MogramSettings.prefs().edit().putLong("mogram_link_state_ts", ts).apply();
        MogramSettings.importShared(json);
    }
}
