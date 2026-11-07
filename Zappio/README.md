# Zappio

Мессенджер: Kotlin + Jetpack Compose (Material You), Firebase Auth (вход по SMS) + Cloud Firestore.
Нижний док-бар в стиле glassmorphism: Главная · Контакты (поиск по номеру) · Профиль.

## Настройка Firebase (один раз)
1. **Authentication → Sign-in method → Phone** — включить.
2. **Project settings → Your apps → Android (app.zappio.messenger)** → добавить отпечатки из `app/debug.keystore`:
   - SHA-1:   `EF:80:84:1B:43:81:A6:49:77:B4:12:CE:BC:00:1B:70:AB:C4:4B:18`
   - SHA-256: `DB:76:2E:B7:96:3B:4D:6C:FE:84:1D:2F:4E:DD:52:EA:03:83:46:57:10:81:37:DD:41:6B:66:48:32:E2:A5:A6`
   затем **скачать новый google-services.json** и заменить `app/google-services.json`.
3. **Firestore Database** — создать базу и вставить правила из `firestore.rules` (вкладка Rules → Publish).
4. Для теста без реальных SMS: Authentication → Phone → *Phone numbers for testing*.

## Сборка APK
Залей проект в GitHub → **Actions → Build APK** → артефакт **Zappio-debug-apk**.
