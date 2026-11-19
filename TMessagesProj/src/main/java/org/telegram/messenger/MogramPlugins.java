package org.telegram.messenger;

import java.io.File;

// Mogram: точка входа плагинов. Сам загрузчик (Python) лежит в сборке Standalone и регистрируется здесь.
public class MogramPlugins {

    public interface Host {
        // true, если текст был командой плагина и отправлять его не нужно
        boolean runCommand(String text, long dialogId, int replyToId);

        // JSON-список плагинов: [{id, file, name, description, version}]
        String describe();

        // копирует файл .plugin в папку плагинов и перезагружает их
        boolean install(File file);

        void reload();
    }

    public static volatile Host host;

    public static File pluginsDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "mogram_plugins");
        dir.mkdirs();
        return dir;
    }
}
