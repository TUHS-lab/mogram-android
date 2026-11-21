package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MogramPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

// Mogram: окно плагинов: список установленных и установка из файла .plugin.
public class MogramPluginsActivity extends BaseFragment {

    private static final int PICK_PLUGIN = 9201;

    private final ArrayList<String[]> items = new ArrayList<>();
    private ArrayAdapter<String[]> adapter;
    private TextView hintView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(context.getString(R.string.MogramPlugins));
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

        TextView install = new TextView(context);
        install.setText(context.getString(R.string.MogramPluginsInstall));
        install.setTypeface(Typeface.DEFAULT_BOLD);
        install.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        install.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        install.setOnClickListener(v -> pickFile());
        root.addView(install, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hintView = new TextView(context);
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hintView.setTextSize(13);
        hintView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(hintView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        adapter = new ArrayAdapter<String[]>(context, android.R.layout.simple_list_item_2, android.R.id.text1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String[] item = getItem(position);
                TextView title = view.findViewById(android.R.id.text1);
                TextView subtitle = view.findViewById(android.R.id.text2);
                title.setText(item[0]);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                subtitle.setText(item[1]);
                subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                return view;
            }
        };
        ListView list = new ListView(context);
        list.setAdapter(adapter);
        list.setDividerHeight(0);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        refresh(context);
        return fragmentView;
    }

    private void refresh(Context context) {
        items.clear();
        MogramPlugins.Host host = MogramPlugins.host;
        if (host == null) {
            hintView.setText(context.getString(R.string.MogramPluginsUnavailable));
        } else {
            hintView.setText(context.getString(R.string.MogramPluginsHint));
            try {
                JSONArray array = new JSONArray(host.describe());
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject object = array.getJSONObject(i);
                    String version = object.optString("version");
                    items.add(new String[]{
                            object.optString("name") + (version.isEmpty() ? "" : " " + version),
                            object.optString("description")
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void pickFile() {
        if (getParentActivity() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        getParentActivity().startActivityForResult(intent, PICK_PLUGIN);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_PLUGIN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Context context = getParentActivity();
        MogramPlugins.Host host = MogramPlugins.host;
        if (context == null || host == null) {
            return;
        }
        try {
            Uri uri = data.getData();
            String name = "plugin.plugin";
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        name = cursor.getString(index);
                    }
                }
                cursor.close();
            }
            if (!name.endsWith(".plugin")) {
                Toast.makeText(context, context.getString(R.string.MogramPluginsNotPlugin), Toast.LENGTH_LONG).show();
                return;
            }
            File temp = new File(ApplicationLoader.applicationContext.getCacheDir(), name.replace('/', '_'));
            InputStream in = context.getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            boolean ok = host.install(temp);
            temp.delete();
            Toast.makeText(context, context.getString(ok ? R.string.MogramPluginsInstalled : R.string.MogramPluginsFailed), Toast.LENGTH_LONG).show();
            AndroidUtilities.runOnUIThread(() -> refresh(context), 1500);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context, context.getString(R.string.MogramPluginsFailed), Toast.LENGTH_LONG).show();
        }
    }
}
