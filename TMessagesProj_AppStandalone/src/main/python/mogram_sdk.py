"""Mogram для Android: объект api для плагинов.

Каждый вызов превращается в вызов Java-класса MogramBridge. Реализованы основные
действия: отправка, тост, буфер обмена, окно сообщения и сведения о себе.
"""
import json

from java import jclass

_Bridge = jclass("org.telegram.messenger.MogramBridge")


class _Api:
    def show_toast(self, text):
        _Bridge.toast(str(text))

    def show_bulletin(self, text, kind=None, subtitle=None, button=None, on_click=None, duration=None):
        _Bridge.toast(str(text))

    def copy_text(self, text):
        _Bridge.copy(str(text))

    def send_message(self, peer, text, **kwargs):
        _Bridge.sendMessage(int(peer), str(text))

    def get_me(self):
        return json.loads(str(_Bridge.me()))

    def show_dialog(self, title, message="", fields=None, buttons=None, on_result=None, on_close=None):
        _Bridge.alert(str(title), str(message))
        return 0

    def open_url(self, url):
        _Bridge.openUrl(str(url))


api = _Api()
