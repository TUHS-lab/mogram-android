package org.telegram.messenger;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

// Mogram: загрузчик плагинов на Python (Chaquopy). Живёт только в сборке Standalone.
public class MogramPythonHost implements MogramPlugins.Host, NotificationCenter.NotificationCenterDelegate {

    private final PyObject module;

    private MogramPythonHost(PyObject module) {
        this.module = module;
    }

    public static void start(Context context) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(context));
                }
                PyObject module = Python.getInstance().getModule("mogram_host");
                module.callAttr("init", MogramPlugins.pluginsDir().getAbsolutePath());
                final MogramPythonHost host = new MogramPythonHost(module);
                MogramPlugins.host = host;
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
                        NotificationCenter.getInstance(a).addObserver(host, NotificationCenter.didReceiveNewMessages);
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public boolean runCommand(String text, long dialogId, int replyToId) {
        try {
            return module.callAttr("run_command", text, dialogId, replyToId).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    @Override
    public String describe() {
        try {
            return module.callAttr("describe").toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "[]";
        }
    }

    @Override
    public boolean install(File source) {
        try {
            File target = new File(MogramPlugins.pluginsDir(), source.getName().endsWith(".plugin") ? source.getName() : source.getName() + ".plugin");
            FileInputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            reload();
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    @Override
    public String menuItems(String type) {
        try {
            return module.callAttr("menu_items", type).toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "[]";
        }
    }

    @Override
    public void menuClick(int index, String ctxJson) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                module.callAttr("menu_click", index, ctxJson);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public void reload() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                module.callAttr("reload_all");
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || args.length < 2 || !(args[1] instanceof ArrayList)) {
            return;
        }
        final ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        final ArrayList<String> payloads = new ArrayList<>();
        for (MessageObject message : messages) {
            try {
                JSONObject object = new JSONObject();
                object.put("id", message.getId());
                object.put("peer", message.getDialogId());
                object.put("text", message.messageText == null ? "" : message.messageText.toString());
                object.put("out", message.isOutOwner());
                object.put("date", message.messageOwner == null ? 0 : message.messageOwner.date);
                object.put("from", message.getFromChatId());
                final long dialogId = message.getDialogId();
                String kind = "user";
                String title = "";
                if (dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                    kind = ChatObject.isChannelAndNotMegaGroup(chat) ? "channel" : "group";
                    title = chat == null || chat.title == null ? "" : chat.title;
                } else {
                    TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
                    title = user == null ? "" : UserObject.getUserName(user);
                }
                object.put("peer_kind", kind);
                object.put("peer_title", title);
                payloads.add(object.toString());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            for (String payload : payloads) {
                try {
                    module.callAttr("dispatch_new_message", payload);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }
}
