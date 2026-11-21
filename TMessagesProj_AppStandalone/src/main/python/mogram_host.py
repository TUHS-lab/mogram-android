"""Mogram для Android: загрузчик плагинов. Вызывается из Java (MogramPythonHost)."""
import asyncio
import inspect
import json
import os
import re
import traceback

import base_plugin
from mogram_sdk import api

_plugins = {}     # id -> экземпляр
_files = {}       # id -> имя файла
_errors = {}      # имя файла -> текст ошибки
_commands = {}    # имя команды -> (плагин, метод)
_plugins_dir = ""
_menu_flat = []   # плоский список (плагин, пункт) для текущего показа меню


class _Ctx:
    def __init__(self, peer, text, args, reply_to):
        self.peer = peer
        self.text = text
        self.args = args
        self.args_list = args.split() if args else []
        self.reply_to = reply_to
        self.prefix = text[:1]
        self.name = ""
        self._sent = False

    def reply(self, text):
        api.send_message(self.peer, text)

    async def edit(self, text):
        api.send_message(self.peer, text)


def init(plugins_dir):
    global _plugins_dir
    _plugins_dir = plugins_dir
    os.makedirs(plugins_dir, exist_ok=True)
    reload_all()


def reload_all():
    for plugin in list(_plugins.values()):
        try:
            plugin.on_plugin_unload()
        except Exception:
            pass
    _plugins.clear()
    _files.clear()
    _errors.clear()
    _commands.clear()
    for name in sorted(os.listdir(_plugins_dir)):
        if name.endswith(".plugin"):
            _load_file(os.path.join(_plugins_dir, name))


def _load_file(path):
    name = os.path.basename(path)
    try:
        source = open(path, encoding="utf-8").read()
        namespace = {"__name__": "plugin_" + re.sub(r"\W", "_", name)}
        exec(compile(source, path, "exec"), namespace)
        classes = [value for value in namespace.values()
                   if inspect.isclass(value) and issubclass(value, base_plugin.BasePlugin) and value is not base_plugin.BasePlugin]
        if not classes:
            raise ValueError("в файле нет класса, наследующего BasePlugin")
        plugin = classes[0]()
        plugin_id = namespace.get("__id__") or name[:-7]
        plugin.id = plugin_id
        plugin.meta = {key: namespace.get(key) for key in ("__name__", "__description__", "__author__", "__version__")}
        plugin.on_plugin_load()
        _plugins[plugin_id] = plugin
        _files[plugin_id] = name
        for attr in dir(plugin):
            method = getattr(plugin, attr, None)
            info = getattr(method, "_mog_command", None)
            if info:
                for alias in [info["name"]] + info["aliases"]:
                    _commands[alias.lower()] = (plugin, method)
    except Exception:
        _errors[name] = traceback.format_exc(limit=3)


def describe():
    """JSON-список установленных плагинов и ошибок для окна «Плагины»."""
    items = []
    for plugin_id, plugin in _plugins.items():
        items.append({"id": plugin_id, "file": _files[plugin_id], "name": plugin.meta.get("__name__") or plugin_id,
                      "description": plugin.meta.get("__description__") or "", "version": plugin.meta.get("__version__") or ""})
    for name, error in _errors.items():
        items.append({"id": "", "file": name, "name": name, "description": "ошибка: " + error.strip().splitlines()[-1], "version": ""})
    return json.dumps(items, ensure_ascii=False)


def menu_items(kind):
    """JSON-список пунктов меню плагинов для места kind (message_context_menu и т. д.)."""
    del _menu_flat[:]
    result = []
    for plugin in _plugins.values():
        for item in list(getattr(plugin, "_menu_items", {}).values()):
            if item.menu_type == kind and item.on_click:
                result.append({"i": len(_menu_flat), "text": str(item.text), "icon": item.icon or ""})
                _menu_flat.append((plugin, item))
    return json.dumps(result, ensure_ascii=False)


def menu_click(index, ctx_json):
    try:
        plugin, item = _menu_flat[int(index)]
        item.on_click(json.loads(ctx_json))
    except Exception:
        api.show_toast("Ошибка плагина: " + traceback.format_exc(limit=2).strip().splitlines()[-1])


def run_command(text, peer, reply_to):
    """True, если текст был командой плагина и отправлять его не нужно."""
    text = (text or "").strip()
    if len(text) < 2 or text[0] not in "./":
        return False
    head, _, args = text[1:].partition(" ")
    entry = _commands.get(head.lower())
    if not entry:
        return False
    plugin, method = entry
    ctx = _Ctx(int(peer), text, args.strip(), int(reply_to) if reply_to else None)
    ctx.name = head
    try:
        result = method(ctx)
        if inspect.iscoroutine(result):
            result = asyncio.run(result)
        if isinstance(result, tuple):
            result = result[0]
        if isinstance(result, str) and result:
            api.send_message(ctx.peer, result)
    except Exception:
        api.show_toast("Ошибка плагина: " + traceback.format_exc(limit=2).strip().splitlines()[-1])
    return True


def dispatch_new_message(payload):
    """Событие нового сообщения: on_new_message и @watcher."""
    try:
        m = json.loads(payload)
    except Exception:
        return
    for plugin in list(_plugins.values()):
        try:
            handler = getattr(plugin, "on_new_message", None)
            if handler:
                handler(m)
            for attr in dir(plugin):
                method = getattr(plugin, attr, None)
                info = getattr(method, "_mog_watcher", None)
                if info and _watcher_matches(info, m):
                    method(m)
        except Exception:
            pass


def _watcher_matches(info, m):
    if info.get("incoming") and m.get("out"):
        return False
    if info.get("out") is not None and bool(info["out"]) != bool(m.get("out")):
        return False
    chats = info.get("chats")
    if chats:
        wanted = chats if isinstance(chats, (list, tuple)) else [chats]
        if m.get("peer_kind") not in wanted:
            return False
    if info.get("regex") and not re.search(info["regex"], m.get("text", ""), re.IGNORECASE):
        return False
    return True
