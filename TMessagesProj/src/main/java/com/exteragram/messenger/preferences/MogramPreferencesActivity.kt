package com.exteragram.messenger.preferences

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MogramBackground
import org.telegram.messenger.MogramLink
import org.telegram.messenger.MogramSettings
import org.telegram.messenger.MogramViewers
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.PrivacySettingsActivity
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.text.DateFormat
import java.util.ArrayList
import java.util.Date

// Mogram: связь с ПК, журнал зрителей историй, фон чата. Экран собран на каркасе настроек exteraGram.
class MogramPreferencesActivity : BasePreferencesActivity() {

    enum class Item {
        LINK_ENABLED,
        LINK_PASSWORD,
        LINK_CLIP,
        LINK_MUSIC,
        LINK_NOTES,
        LINK_ACTIVITY,
        VIEWERS_ENABLED,
        VIEWERS_OPEN,
        VIEWERS_CLEAR,
        BACKGROUND,
        PRIVACY;

        val id: Int = ordinal + 1
    }

    private fun ru() = LocaleController.getInstance().currentLocale.language == "ru"
    private fun t(en: String, ru: String) = if (ru()) ru else en

    override fun getTitle(): String = "Mogram"

    private fun flag(name: String) = MogramSettings.prefs().getBoolean("mogram_link_$name", true)

    private fun setFlag(name: String, value: Boolean) {
        MogramSettings.prefs().edit().putBoolean("mogram_link_$name", value).apply()
    }

    private fun presetName(preset: Int): String = when (preset) {
        MogramBackground.PRESET_OFF -> t("Telegram default", "Как в Telegram")
        MogramBackground.PRESET_GRAPHITE -> t("Graphite", "Графит")
        MogramBackground.PRESET_AURORA -> t("Aurora", "Аврора")
        MogramBackground.PRESET_SUNSET -> t("Sunset", "Закат")
        MogramBackground.PRESET_OCEAN -> t("Ocean", "Океан")
        else -> t("Forest", "Лес")
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(t("Mogram Link (PC and phone)", "Mogram Link (ПК и телефон)")))
        items.add(uCheck(Item.LINK_ENABLED, t("Enable link", "Включить связь"), MogramLink.enabled(),
            t("Devices talk through a private channel \"Mogram Link\" in your account", "Устройства общаются через закрытый канал «Mogram Link» в вашем аккаунте")))
        val hasPassword = MogramSettings.prefs().getString("mogram_link_password", "")!!.isNotEmpty()
        items.add(uButton(Item.LINK_PASSWORD, t("Link password", "Пароль связи"), value = if (hasPassword) t("set", "задан") else t("not set", "не задан")))
        items.add(uCheck(Item.LINK_CLIP, t("Shared clipboard", "Общий буфер обмена"), flag("clip")))
        items.add(uCheck(Item.LINK_MUSIC, t("Music between devices", "Музыка между устройствами"), flag("music")))
        items.add(uCheck(Item.LINK_NOTES, t("Shared notes, tags and local pins", "Общие заметки, теги и локальные закрепы"), flag("notes")))
        items.add(uCheck(Item.LINK_ACTIVITY, t("Smart activity handoff", "Умный перехват активности"), flag("activity"),
            t("No notification here while you read that chat on the other device", "Нет уведомления здесь, пока вы читаете этот чат на другом устройстве")))
        items.add(UItem.asShadow(t("The same password must be set on both devices. Messages are encrypted (AES-256-GCM).", "На обоих устройствах должен быть один пароль. Сообщения шифруются (AES-256-GCM).")))

        items.add(UItem.asHeader(t("Story viewers journal", "Журнал зрителей историй")))
        items.add(uCheck(Item.VIEWERS_ENABLED, t("Keep journal", "Вести журнал"), MogramViewers.enabled(),
            t("Stored only on this device. Telegram does not reveal who opens your profile, so profile views cannot be logged.",
                "Хранится только на этом устройстве. Telegram не сообщает, кто открывал ваш профиль, поэтому просмотры профиля вести нельзя.")))
        items.add(uButton(Item.VIEWERS_OPEN, t("Open journal", "Открыть журнал"), value = MogramViewers.entries().size.toString()))
        items.add(uButton(Item.VIEWERS_CLEAR, t("Clear journal", "Очистить журнал"), red = true))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(t("Chat background", "Фон чата")))
        items.add(uButton(Item.BACKGROUND, t("Gradient palette", "Градиентная палитра"), value = presetName(MogramBackground.preset())))
        items.add(UItem.asShadow(t("Applies to the dark theme after restarting the app.", "Применяется к тёмной теме после перезапуска приложения.")))

        items.add(UItem.asHeader(t("Privacy", "Приватность")))
        items.add(uButton(Item.PRIVACY, t("Ghost mode and privacy", "Призрак и приватность")))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > Item.entries.size) return
        when (Item.entries[item.id - 1]) {
            Item.LINK_ENABLED -> toggleBooleanSettingAndRefresh(item) {
                MogramSettings.prefs().edit().putBoolean("mogram_link", it).apply()
                if (it) MogramLink.get().start(currentAccount) else MogramLink.get().stop()
            }
            Item.LINK_PASSWORD -> askPassword()
            Item.LINK_CLIP -> toggleBooleanSettingAndRefresh(item) { setFlag("clip", it) }
            Item.LINK_MUSIC -> toggleBooleanSettingAndRefresh(item) { setFlag("music", it) }
            Item.LINK_NOTES -> toggleBooleanSettingAndRefresh(item) { setFlag("notes", it) }
            Item.LINK_ACTIVITY -> toggleBooleanSettingAndRefresh(item) { setFlag("activity", it) }
            Item.VIEWERS_ENABLED -> toggleBooleanSettingAndRefresh(item) { MogramViewers.setEnabled(it) }
            Item.VIEWERS_OPEN -> showJournal()
            Item.VIEWERS_CLEAR -> {
                MogramViewers.clear()
                listView.adapter?.update(true)
            }
            Item.BACKGROUND -> {
                val names = Array<CharSequence>(MogramBackground.COUNT) { presetName(it) }
                val builder = AlertDialog.Builder(context, resourceProvider)
                builder.setTitle(t("Gradient palette", "Градиентная палитра"))
                builder.setItems(names) { _, which ->
                    MogramBackground.setPreset(which)
                    listView.adapter?.update(true)
                }
                showDialog(builder.create())
            }
            Item.PRIVACY -> presentFragment(PrivacySettingsActivity())
        }
    }

    private fun askPassword() {
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setSingleLine(true)
        input.hint = t("Same on all devices", "Один на всех устройствах")
        val box = LinearLayout(context)
        box.setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(8f), AndroidUtilities.dp(24f), 0)
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val builder = AlertDialog.Builder(context, resourceProvider)
        builder.setTitle(t("Link password", "Пароль связи"))
        builder.setView(box)
        builder.setPositiveButton(t("Save", "Сохранить")) { _, _ ->
            MogramSettings.prefs().edit().putString("mogram_link_password", input.text.toString()).apply()
            MogramLink.get().stop()
            if (MogramLink.enabled()) MogramLink.get().start(currentAccount)
            listView.adapter?.update(true)
        }
        builder.setNegativeButton(t("Cancel", "Отмена"), null)
        showDialog(builder.create())
    }

    private fun showJournal() {
        val entries = MogramViewers.entries()
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val text = if (entries.isEmpty()) {
            t("Empty. Entries appear after someone views your stories.", "Пусто. Записи появятся, когда кто-то посмотрит ваши истории.")
        } else {
            entries.take(200).joinToString("\n") {
                val name = it.optString("n").ifEmpty { "id " + it.optLong("u") }
                "$name · ${format.format(Date(it.optInt("d") * 1000L))}"
            }
        }
        val builder = AlertDialog.Builder(context, resourceProvider)
        builder.setTitle(t("Story viewers", "Зрители историй"))
        builder.setMessage(text)
        builder.setPositiveButton("OK", null)
        showDialog(builder.create())
    }
}
