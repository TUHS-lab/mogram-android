package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_stories;

import java.util.ArrayList;
import java.util.HashSet;

// Mogram: журнал «кто смотрел ваши истории». Telegram показывает зрителей только пока история жива,
// поэтому Mogram раз в полчаса забирает список и хранит его у вас на устройстве.
// Просмотры профиля Telegram не раскрывает никому, их журнал вести нельзя.
public final class MogramViewers {

    private static final String KEY = "mogram_viewers";
    private static long lastRefresh;

    private MogramViewers() {
    }

    public static boolean enabled() {
        return MogramSettings.prefs().getBoolean("mogram_viewers_journal", false);
    }

    public static void setEnabled(boolean value) {
        MogramSettings.prefs().edit().putBoolean("mogram_viewers_journal", value).apply();
    }

    public static void refresh(int account) {
        if (!enabled() || System.currentTimeMillis() - lastRefresh < 30 * 60 * 1000L) {
            return;
        }
        lastRefresh = System.currentTimeMillis();
        final TL_stories.TL_stories_getPeerStories req = new TL_stories.TL_stories_getPeerStories();
        req.peer = new org.telegram.tgnet.TLRPC.TL_inputPeerSelf();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (!(response instanceof TL_stories.TL_stories_peerStories)) {
                return;
            }
            final TL_stories.PeerStories peer = ((TL_stories.TL_stories_peerStories) response).stories;
            if (peer == null) {
                return;
            }
            for (TL_stories.StoryItem item : peer.stories) {
                if (item != null && item.id != 0) {
                    loadViews(account, item.id);
                }
            }
        });
    }

    private static void loadViews(int account, int storyId) {
        final TL_stories.TL_stories_getStoryViewsList req = new TL_stories.TL_stories_getStoryViewsList();
        req.peer = MessagesController.getInstance(account).getInputPeer(UserConfig.getInstance(account).getClientUserId());
        req.id = storyId;
        req.q = "";
        req.offset = "";
        req.limit = 100;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (!(response instanceof TL_stories.StoryViewsList)) {
                return;
            }
            final TL_stories.StoryViewsList list = (TL_stories.StoryViewsList) response;
            final MessagesController controller = MessagesController.getInstance(account);
            controller.putUsers(list.users, false);
            final ArrayList<JSONObject> add = new ArrayList<>();
            for (TL_stories.StoryView view : list.views) {
                try {
                    final org.telegram.tgnet.TLRPC.User user = controller.getUser(view.user_id);
                    final JSONObject entry = new JSONObject();
                    entry.put("u", view.user_id);
                    entry.put("n", user == null ? "" : UserObject.getUserName(user));
                    entry.put("s", storyId);
                    entry.put("d", view.date);
                    add.add(entry);
                } catch (Throwable ignore) {
                }
            }
            merge(add);
        });
    }

    private static synchronized void merge(ArrayList<JSONObject> add) {
        try {
            final JSONArray old = new JSONArray(MogramSettings.prefs().getString(KEY, "[]"));
            final HashSet<String> seen = new HashSet<>();
            final ArrayList<JSONObject> all = new ArrayList<>(add);
            for (JSONObject e : add) {
                seen.add(e.optLong("u") + ":" + e.optInt("s"));
            }
            for (int i = 0; i < old.length(); i++) {
                final JSONObject e = old.getJSONObject(i);
                if (seen.add(e.optLong("u") + ":" + e.optInt("s"))) {
                    all.add(e);
                }
            }
            all.sort((a, b) -> Integer.compare(b.optInt("d"), a.optInt("d")));
            final JSONArray out = new JSONArray();
            for (int i = 0; i < Math.min(all.size(), 1000); i++) {
                out.put(all.get(i));
            }
            MogramSettings.prefs().edit().putString(KEY, out.toString()).apply();
        } catch (Throwable ignore) {
        }
    }

    public static ArrayList<JSONObject> entries() {
        final ArrayList<JSONObject> out = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(MogramSettings.prefs().getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                out.add(array.getJSONObject(i));
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    public static void clear() {
        MogramSettings.prefs().edit().remove(KEY).apply();
    }
}
