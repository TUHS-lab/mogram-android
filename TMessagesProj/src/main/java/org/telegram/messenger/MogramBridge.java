package org.telegram.messenger;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

// Mogram: методы, которые плагины на Python вызывают через mogram_sdk.api.
public class MogramBridge {

    public static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show());
    }

    public static void copy(String text) {
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.addToClipboard(text));
    }

    public static void sendMessage(long peer, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() ->
                SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer)));
    }

    public static String me() {
        try {
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            JSONObject object = new JSONObject();
            if (user != null) {
                object.put("id", user.id);
                object.put("first_name", user.first_name);
                object.put("last_name", user.last_name);
                object.put("username", user.username);
            }
            return object.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static void alert(String title, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            if (LaunchActivity.instance == null) {
                toast(message);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.instance);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton("OK", null);
            builder.show();
        });
    }

    public static void openUrl(String url) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ApplicationLoader.applicationContext.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }
}
