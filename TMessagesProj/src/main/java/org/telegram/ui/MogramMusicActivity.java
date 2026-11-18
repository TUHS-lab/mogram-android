package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Mogram: простой плеер SoundCloud: поиск и воспроизведение треков.
public class MogramMusicActivity extends BaseFragment {

    private static class Track {
        String title;
        String artist;
        String transcodingUrl;

        @Override
        public String toString() {
            return artist + " - " + title;
        }
    }

    private static final String API = "https://api-v2.soundcloud.com";
    private static final String PREFS = "mogram_music";

    private EditText searchInput;
    private ListView listView;
    private TextView statusView;
    private TextView nowPlayingView;
    private TextView pauseButton;
    private ArrayAdapter<Track> adapter;
    private final ArrayList<Track> tracks = new ArrayList<>();
    private MediaPlayer player;
    private volatile int playGeneration;
    private String clientId;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(context.getString(R.string.MogramMusic));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        searchInput = new EditText(context);
        searchInput.setHint(context.getString(R.string.MogramMusicSearch));
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(searchInput.getText().toString().trim());
                return true;
            }
            return false;
        });
        root.addView(searchInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setTextSize(13);
        statusView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        adapter = new ArrayAdapter<Track>(context, android.R.layout.simple_list_item_2, android.R.id.text1, tracks) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Track track = getItem(position);
                TextView title = view.findViewById(android.R.id.text1);
                TextView artist = view.findViewById(android.R.id.text2);
                title.setText(track.title);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                artist.setText(track.artist);
                artist.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                return view;
            }
        };
        listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setDividerHeight(0);
        listView.setOnItemClickListener((parent, view, position, id) -> play(tracks.get(position)));
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bar.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        nowPlayingView = new TextView(context);
        nowPlayingView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nowPlayingView.setSingleLine(true);
        nowPlayingView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowPlayingView.setText(context.getString(R.string.MogramMusicNothing));
        bar.addView(nowPlayingView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pauseButton = new TextView(context);
        pauseButton.setText(context.getString(R.string.MogramMusicPause));
        pauseButton.setTypeface(Typeface.DEFAULT_BOLD);
        pauseButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        pauseButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        pauseButton.setOnClickListener(v -> togglePause());
        bar.addView(pauseButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        playGeneration++;
        releasePlayer();
        super.onFragmentDestroy();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception e) {
                FileLog.e(e);
            }
            player = null;
        }
    }

    private void togglePause() {
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                pauseButton.setText(getParentActivity().getString(R.string.MogramMusicPlay));
            } else {
                player.start();
                pauseButton.setText(getParentActivity().getString(R.string.MogramMusicPause));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void setStatus(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            if (statusView != null) {
                statusView.setText(text);
            }
        });
    }

    private static String httpGet(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                result.append(buffer, 0, read);
            }
            reader.close();
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // client_id берётся из скриптов главной страницы SoundCloud, как у веб-плеера.
    private String findClientId(boolean refresh) throws Exception {
        if (!refresh) {
            String saved = prefs().getString("client_id", "");
            if (!saved.isEmpty()) {
                return saved;
            }
        }
        String home = httpGet("https://soundcloud.com/");
        Matcher scripts = Pattern.compile("<script[^>]+src=\"(https://[^\"]+\\.js)\"").matcher(home);
        ArrayList<String> urls = new ArrayList<>();
        while (scripts.find()) {
            urls.add(scripts.group(1));
        }
        for (int i = urls.size() - 1; i >= 0; --i) {
            try {
                Matcher id = Pattern.compile("client_id[\"']?\\s*[:=]\\s*[\"']([A-Za-z0-9]{32})[\"']").matcher(httpGet(urls.get(i)));
                if (id.find()) {
                    prefs().edit().putString("client_id", id.group(1)).apply();
                    return id.group(1);
                }
            } catch (Exception ignore) {
            }
        }
        throw new Exception("client_id");
    }

    private void search(String query) {
        if (query.isEmpty()) {
            return;
        }
        setStatus(getParentActivity().getString(R.string.MogramMusicSearching));
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ArrayList<Track> found = searchTracks(query, false);
                AndroidUtilities.runOnUIThread(() -> {
                    tracks.clear();
                    tracks.addAll(found);
                    adapter.notifyDataSetChanged();
                    statusView.setText(found.isEmpty() ? getParentActivity().getString(R.string.MogramMusicEmpty) : "");
                });
            } catch (Exception e) {
                FileLog.e(e);
                setStatus(getParentActivity().getString(R.string.MogramMusicError));
            }
        });
    }

    private ArrayList<Track> searchTracks(String query, boolean retried) throws Exception {
        if (clientId == null) {
            clientId = findClientId(false);
        }
        String body;
        try {
            body = httpGet(API + "/search/tracks?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=25&client_id=" + clientId);
        } catch (Exception e) {
            if (retried) {
                throw e;
            }
            clientId = findClientId(true);
            return searchTracks(query, true);
        }
        JSONArray collection = new JSONObject(body).optJSONArray("collection");
        ArrayList<Track> result = new ArrayList<>();
        if (collection == null) {
            return result;
        }
        for (int i = 0; i < collection.length(); ++i) {
            JSONObject item = collection.getJSONObject(i);
            JSONObject media = item.optJSONObject("media");
            JSONArray transcodings = media == null ? null : media.optJSONArray("transcodings");
            String progressive = null;
            if (transcodings != null) {
                for (int j = 0; j < transcodings.length(); ++j) {
                    JSONObject t = transcodings.getJSONObject(j);
                    JSONObject format = t.optJSONObject("format");
                    if (format != null && "progressive".equals(format.optString("protocol")) && format.optString("mime_type").contains("mpeg")) {
                        progressive = t.optString("url");
                        break;
                    }
                }
            }
            if (progressive == null || progressive.isEmpty()) {
                continue;
            }
            Track track = new Track();
            track.title = item.optString("title");
            JSONObject user = item.optJSONObject("user");
            track.artist = user == null ? "" : user.optString("username");
            track.transcodingUrl = progressive;
            result.add(track);
        }
        return result;
    }

    private void play(Track track) {
        final int generation = ++playGeneration;
        releasePlayer();
        nowPlayingView.setText(track.toString());
        setStatus(getParentActivity().getString(R.string.MogramMusicLoading));
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String json = httpGet(track.transcodingUrl + (track.transcodingUrl.contains("?") ? "&" : "?") + "client_id=" + clientId);
                String stream = new JSONObject(json).getString("url");
                if (generation != playGeneration) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != playGeneration) {
                        return;
                    }
                    try {
                        MediaPlayer next = new MediaPlayer();
                        next.setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build());
                        next.setDataSource(stream);
                        next.setOnPreparedListener(mp -> {
                            if (generation == playGeneration) {
                                mp.start();
                                statusView.setText("");
                                pauseButton.setText(getParentActivity().getString(R.string.MogramMusicPause));
                            }
                        });
                        next.setOnErrorListener((mp, what, extra) -> {
                            statusView.setText(getParentActivity().getString(R.string.MogramMusicError));
                            return true;
                        });
                        player = next;
                        next.prepareAsync();
                    } catch (Exception e) {
                        FileLog.e(e);
                        statusView.setText(getParentActivity().getString(R.string.MogramMusicError));
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                setStatus(getParentActivity().getString(R.string.MogramMusicError));
            }
        });
    }
}
