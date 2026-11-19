"""Тесты движка плагинов Mogram (Python-часть) без телефона.

Вместо Java подставляется поддельный модуль `java` с классом MogramBridge, который записывает вызовы.
Запуск: python tests/test_plugins.py. Код выхода 1 при ошибках.
"""
import json
import os
import pathlib
import sys
import tempfile
import types

root = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root / "TMessagesProj_AppStandalone" / "src" / "main" / "python"))

calls = []


class FakeBridge:
    @staticmethod
    def toast(text):
        calls.append(("toast", text))

    @staticmethod
    def copy(text):
        calls.append(("copy", text))

    @staticmethod
    def sendMessage(peer, text):
        calls.append(("send", peer, text))

    @staticmethod
    def me():
        return json.dumps({"id": 1, "first_name": "Тест"})

    @staticmethod
    def alert(title, message):
        calls.append(("alert", title, message))

    @staticmethod
    def openUrl(url):
        calls.append(("url", url))


fake_java = types.ModuleType("java")
fake_java.jclass = lambda name: FakeBridge
sys.modules["java"] = fake_java

import mogram_host  # noqa: E402

failures = 0
checks = 0


def eq(name, expected, actual):
    global failures, checks
    checks += 1
    if expected != actual:
        failures += 1
        print("FAIL %s: ожидалось %r, получено %r" % (name, expected, actual))


PLUGIN_OK = '''
__id__ = "hello"
__name__ = "Привет"
__description__ = "Тестовый плагин"
__version__ = "1.2"

from base_plugin import BasePlugin, MenuItemData, MenuItemType, command, watcher
from mogram_sdk import api


class Hello(BasePlugin):
    def on_plugin_load(self):
        self.seen = []
        self.add_menu_item(MenuItemData(menu_type=MenuItemType.MESSAGE_CONTEXT_MENU, text="Копировать",
                                        on_click=lambda ctx: api.copy_text(ctx["text"])))

    @command("hello", "приветствие", aliases=["hi"])
    def hello(self, ctx):
        return "Привет, %s!" % (ctx.args or "мир")

    @command("echo")
    def echo(self, ctx):
        ctx.reply("эхо: " + ctx.args)

    @command("boom")
    def boom(self, ctx):
        raise RuntimeError("сломано")

    @command("later")
    async def later(self, ctx):
        await ctx.edit("готово")

    @watcher(chats="group", incoming=True, regex="mog")
    def mention(self, m):
        api.show_toast("упомянули в " + m["peer_title"])

    def on_new_message(self, m):
        self.seen.append(m["id"])
'''

PLUGIN_BAD = "this is not python ("
PLUGIN_NO_CLASS = "__id__ = 'nothing'\nx = 1\n"

with tempfile.TemporaryDirectory() as tmp:
    pathlib.Path(tmp, "hello.plugin").write_text(PLUGIN_OK, encoding="utf-8")
    pathlib.Path(tmp, "bad.plugin").write_text(PLUGIN_BAD, encoding="utf-8")
    pathlib.Path(tmp, "empty.plugin").write_text(PLUGIN_NO_CLASS, encoding="utf-8")
    pathlib.Path(tmp, "ignored.txt").write_text("не плагин", encoding="utf-8")

    mogram_host.init(tmp)

    described = json.loads(mogram_host.describe())
    names = {item["file"]: item for item in described}
    eq("описано три файла .plugin", {"hello.plugin", "bad.plugin", "empty.plugin"}, set(names))
    eq("имя плагина", "Привет", names["hello.plugin"]["name"])
    eq("версия плагина", "1.2", names["hello.plugin"]["version"])
    eq("ошибка синтаксиса видна", True, names["bad.plugin"]["description"].startswith("ошибка:"))
    eq("нет класса видно", True, "BasePlugin" in names["empty.plugin"]["description"])

    # команды
    calls.clear()
    eq("команда найдена", True, mogram_host.run_command(".hello Мир", 100, None))
    eq("результат отправлен", [("send", 100, "Привет, Мир!")], calls)

    calls.clear()
    eq("алиас", True, mogram_host.run_command("/HI", 5, None))
    eq("алиас без аргументов", [("send", 5, "Привет, мир!")], calls)

    calls.clear()
    eq("reply", True, mogram_host.run_command(".echo тест", 7, None))
    eq("reply отправляет", [("send", 7, "эхо: тест")], calls)

    calls.clear()
    eq("async-команда", True, mogram_host.run_command(".later", 9, None))
    eq("async edit отправляет", [("send", 9, "готово")], calls)

    calls.clear()
    eq("ошибка в команде не роняет", True, mogram_host.run_command(".boom", 1, None))
    eq("ошибка показана тостом", True, len(calls) == 1 and calls[0][0] == "toast" and "сломано" in calls[0][1])

    calls.clear()
    eq("неизвестная команда не обрабатывается", False, mogram_host.run_command(".nope", 1, None))
    eq("обычный текст", False, mogram_host.run_command("привет", 1, None))
    eq("пустая строка", False, mogram_host.run_command("", 1, None))
    eq("одиночная точка", False, mogram_host.run_command(".", 1, None))
    eq("ничего не отправлено", [], calls)

    # события и watcher
    calls.clear()
    mogram_host.dispatch_new_message(json.dumps({"id": 11, "peer": -5, "text": "про mog", "out": False,
                                                 "peer_kind": "group", "peer_title": "Чат"}))
    eq("watcher сработал в группе", [("toast", "упомянули в Чат")], calls)

    calls.clear()
    mogram_host.dispatch_new_message(json.dumps({"id": 12, "peer": 5, "text": "про mog", "out": False,
                                                 "peer_kind": "user", "peer_title": "Лично"}))
    eq("watcher не в личке", [], calls)

    calls.clear()
    mogram_host.dispatch_new_message(json.dumps({"id": 13, "peer": -5, "text": "про mog", "out": True,
                                                 "peer_kind": "group", "peer_title": "Чат"}))
    eq("watcher не на исходящие", [], calls)

    calls.clear()
    mogram_host.dispatch_new_message(json.dumps({"id": 14, "peer": -5, "text": "другое", "out": False,
                                                 "peer_kind": "group", "peer_title": "Чат"}))
    eq("watcher не по regex", [], calls)

    mogram_host.dispatch_new_message("это не json")  # не должно упасть
    checks += 1

    # меню
    menu = json.loads(mogram_host.menu_items("message_context_menu"))
    eq("пункт меню сообщения", ["Копировать"], [item["text"] for item in menu])
    eq("другое меню пустое", [], json.loads(mogram_host.menu_items("drawer_menu")))
    calls.clear()
    menu = json.loads(mogram_host.menu_items("message_context_menu"))
    mogram_host.menu_click(menu[0]["i"], json.dumps({"id": 1, "text": "образец"}))
    eq("клик по пункту меню", [("copy", "образец")], calls)
    calls.clear()
    mogram_host.menu_click(99, "{}")  # неизвестный индекс не роняет, показывает тост
    eq("неверный индекс даёт тост", True, len(calls) == 1 and calls[0][0] == "toast")

    # перезагрузка подхватывает новые файлы
    pathlib.Path(tmp, "second.plugin").write_text(PLUGIN_OK.replace('"hello"', '"second"').replace("hello", "second"), encoding="utf-8")
    mogram_host.reload_all()
    eq("после перезагрузки файлов больше", 4, len(json.loads(mogram_host.describe())))

print("Проверок: %d, ошибок: %d" % (checks, failures))
sys.exit(1 if failures else 0)
