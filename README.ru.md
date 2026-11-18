<p align="center">
  <img src="TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Mogram" width="96">
</p>

<h1 align="center">Mogram для Android</h1>

<p align="center">
  <b>Русский</b> · <a href="README.md">English</a>
</p>

Android-клиент на основе официального исходного кода [Telegram для Android](https://github.com/DrKLO/Telegram), «младший брат» [Mogram для компьютера](https://github.com/TUHS-lab/mogram).

> **Статус: ранний.** Сейчас в репозитории только перебрендированная основа (имя, иконка, пакет, сборка в CI). Функции Mogram с компьютера переносятся по шагам, см. план ниже. Это не официальный продукт Telegram.

## План

- [x] Перебрендированная основа: имя, иконка, пакет `app.mogram.android`, без официальных ключей API в коде
- [ ] Первая успешная сборка в GitHub Actions (workflow написан, первый запуск упал на подмодулях, исправлено, повторно не запускался)
- [x] Графитовые цвета тёмных тем (скруглённый интерфейс: пока нет)
- [x] Приватность: режим призрака и режим стримера (скрывает ваш номер). Скрытие юзернейма и ID: пока нет
- [x] Сохранение удалённых собеседником сообщений (история правок: пока нет)
- [ ] Безопасность: ложные пароли, автоблокировка
- [ ] Музыка: плеер SoundCloud с текстами песен
- [ ] Быстрые команды и инструменты
- [ ] Плагины

> Отмеченное написано, но **ещё не собрано и не проверено**: первая успешная сборка впереди.

## Собрать самому

Нужны **свои** ключи Telegram API: <https://core.telegram.org/api/obtaining_api_id>. Официальные ключи здесь не используются, этого требуют правила Telegram для сторонних приложений.

### В GitHub Actions (ничего ставить не нужно)

1. Сделайте форк репозитория.
2. В **Settings → Secrets and variables → Actions** добавьте два секрета: `MOGRAM_API_ID` и `MOGRAM_API_HASH`.
3. По желанию, но лучше: `MOGRAM_KEYSTORE_BASE64` (ваш ключ подписи в base64), `MOGRAM_KEYSTORE_PASSWORD`, `MOGRAM_KEY_ALIAS`, `MOGRAM_KEY_PASSWORD`. Без них создаётся временный ключ, и новую сборку нельзя поставить поверх предыдущей.
4. **Actions → Android → Run workflow.** APK появится в артефактах запуска (и в релизе, если указать тег).

### Локально

Android Studio, JDK 17, Android SDK 36, NDK 27.2.12479018. Впишите `api_id` и `api_hash` в `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` и выполните:

```bash
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
```

## Примечания

- **Пуш-уведомлениям** нужен свой проект Firebase: замените оба файла `google-services.json` (в `TMessagesProj_App` и `TMessagesProj_AppStandalone`) своими. Без этого приложение работает, но уведомления приходят, только пока оно запущено.
- Не используйте имя Telegram и его стандартный логотип в своих сборках и публикуйте свой код, как просит оригинальный README.

## Лицензия

GPL-2.0, как и у оригинала. Основано на [DrKLO/Telegram](https://github.com/DrKLO/Telegram), © Telegram FZ-LLC и участники. Изменения © 2026 [@tuhss](https://t.me/tuhss).
