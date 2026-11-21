"""Mogram для Android: совместимый с десктопом слой для плагинов (.plugin).

Реализована основа: класс BasePlugin, декораторы @command и @watcher, заглушки
для пунктов меню и полей настроек. Подробности: docs-plugins-api.md десктопной версии.
"""
import logging


class MenuItemType:
    MESSAGE_CONTEXT_MENU = "message_context_menu"
    CHAT_ACTION_MENU = "chat_action_menu"
    PROFILE_ACTION_MENU = "profile_action_menu"
    ATTACH_MENU = "attach_menu"
    DRAWER_MENU = "drawer_menu"
    MAIN_MENU = "main_menu"
    HEADER_BUTTON = "header_button"
    COMPOSER_BUTTON = "composer_button"


class MenuItemData:
    def __init__(self, menu_type=None, text="", on_click=None, icon=None, priority=0, condition=None):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.icon = icon
        self.priority = priority
        self.condition = condition


class _Field:
    def __init__(self, key=None, text="", default=None, **extra):
        self.key = key
        self.text = text
        self.default = default
        self.extra = extra


class Header(_Field):
    pass


class Text(_Field):
    pass


class Switch(_Field):
    pass


class Selector(_Field):
    pass


class Input(_Field):
    pass


class Password(_Field):
    pass


class Divider(_Field):
    pass


class Button(_Field):
    pass


def command(name, description="", aliases=None):
    """Метод класса становится командой чата: .name аргументы"""
    def wrap(func):
        func._mog_command = {"name": name, "description": description, "aliases": list(aliases or [])}
        return func
    return wrap


def watcher(chats=None, incoming=None, out=None, regex=None, chat_ids=None, from_ids=None, media=None):
    def wrap(func):
        func._mog_watcher = {"chats": chats, "incoming": incoming, "out": out, "regex": regex,
                             "chat_ids": chat_ids, "from_ids": from_ids, "media": media}
        return func
    return wrap


class HookResult:
    def __init__(self, strategy=None, value=None):
        self.strategy = strategy
        self.value = value


class BasePlugin:
    """Базовый класс плагина."""

    def __init__(self):
        self.logger = logging.getLogger("plugin")
        self._menu_items = {}
        self._next_menu_id = 1
        self._settings = {}

    # жизненный цикл
    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    # пункты меню на Android пока только запоминаются
    def add_menu_item(self, item):
        item_id = self._next_menu_id
        self._next_menu_id += 1
        self._menu_items[item_id] = item
        return item_id

    def remove_menu_item(self, item_id):
        self._menu_items.pop(item_id, None)

    # настройки
    def get_setting(self, key, default=None):
        return self._settings.get(key, default)

    def set_setting(self, key, value):
        self._settings[key] = value

    def s(self, key, *args):
        strings = getattr(self, "strings", {})
        table = strings.get("ru") or strings.get("en") or {}
        text = table.get(key, key)
        return text % args if args else text
