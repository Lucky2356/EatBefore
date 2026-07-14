# EatBefore

Локальное Android-приложение для домашнего учёта продуктов: помогает помнить, что
есть дома, вовремя использовать продукты и меньше выбрасывать еду.

> **Статус:** весь обязательный объём **P0 реализован и проверен на устройстве** —
> включая сканер (EAN/QR/DataMatrix и коды «Честного знака»), OCR срока, уведомления,
> список покупок, аналитику и резервное копирование
> (см. [ROADMAP.md](ROADMAP.md) и [PROJECT_STATE.md](PROJECT_STATE.md)).

## Возможности (реализовано в Foundation)

- Локальная база данных (Room) с разделением «карточка продукта» и «партия запаса».
- Несколько партий одного продукта с независимыми сроками и местами хранения.
- Ручное добавление продукта за пару касаний (быстрые пресеты срока годности).
- Главный экран: скоро истекающие, недавние, общее количество, быстрые действия.
- Запасы: поиск (по названию/бренду/штрихкоду с debounce), фильтр по месту, сортировка.
- Карточка продукта: открыть, уменьшить, «закончилось», выбросить, переместить.
- Полная история всех действий; восстановление и отмена последнего действия.
- Предустановленные места хранения (холодильник, морозильник, шкаф, кладовая).
- **Сканер** штрихкодов/QR/DataMatrix (CameraX + ML Kit, on-device), фонарик, ручной ввод.
- **Поиск товара по коду** через Open Food Facts с локальным кешем (офлайн после первого раза).
- **OCR срока годности** по фото упаковки (ML Kit, on-device) с подтверждением пользователем.
- **Уведомления** о сроках (WorkManager): пакетное напоминание, настройки времени/дней/тихих часов.
- **Список покупок**: группировка по категориям, «купил → в запасы», предложение при списании.
- **«Честный знак» (GS1 DataMatrix)**: GTIN и срок годности извлекаются прямо из кода маркировки.
- **Аналитика**: добавлено/использовано/выброшено/просрочено, «использовано вовремя», регулярные покупки.
- **Резервное копирование**: экспорт/импорт JSON с версией схемы и проверкой целостности.
- Светлая/тёмная темы, динамические цвета (Android 12+), статус-бейджи (иконка+текст).
- Онбординг (3 экрана), empty/error/loading-состояния, undo через Snackbar.

## Технологический стек

Kotlin · Jetpack Compose · Material 3 · MVVM (однонаправленный UiState) ·
Room · Hilt · Coroutines/Flow · Navigation Compose · DataStore · WorkManager (задел) ·
Coil. Готовые точки расширения: `ProductCatalogProvider` (внешний каталог),
`ExpiryDateOcrProvider` (OCR), CameraX + ML Kit (сканер) — в roadmap.

- **minSdk 26**, **targetSdk/compileSdk 36**, JVM target 17.
- Single-module (`:app`) с чётким пакетным разделением (core / domain / data / feature),
  готовым к последующему выделению модулей (см. [ARCHITECTURE.md](ARCHITECTURE.md)).

## Требования для сборки

- JDK 17+ (подходит JBR из состава Android Studio, JDK 21).
- Android SDK Platform 36 + build-tools (ставятся Android Studio).
- Файл `local.properties` с `sdk.dir` (создаётся автоматически Android Studio;
  в репозиторий **не** коммитится).

## Сборка и запуск

```bash
# Debug APK (результат: app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# Установить на подключённое устройство/эмулятор
./gradlew :app:installDebug
# или
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

На Windows без глобального Gradle можно указать JDK явно:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

Проще всего открыть проект в Android Studio и нажать **Run**.

## Тестирование

```bash
# Unit + интеграционные тесты (JVM, Robolectric) — use cases, Room, репозитории
./gradlew :app:testDebugUnitTest

# Инструментальные тесты (требуют устройство/эмулятор)
./gradlew :app:connectedDebugAndroidTest
```

Внешние сервисы в тестах заменяются fake-реализациями. Клок абстрагирован
(`AppClock`) для детерминированности.

## Структура проекта

```
app/src/main/java/com/eatbefore/
  core/common        — Result/валидация, AppClock, диспетчеры
  core/designsystem  — тема M3, статус-бейджи, общие компоненты, форматтеры
  core/database      — Room: entity, DAO, converters, миграции, seed
  core/datastore     — настройки (DataStore)
  domain             — модели, интерфейсы репозиториев/провайдеров, use cases (без Android)
  data               — реализации репозиториев, мапперы, fake-провайдеры
  feature/*          — onboarding, home, inventory, addmanual, product, history, placeholder
  navigation         — маршруты, нижняя навигация, NavHost
  di                 — Hilt-модули
  ui                 — корневой каркас приложения
```

## Ограничения текущего этапа

- Compose-UI-тесты полного набора сценариев и detekt/ktlint — в плане качества (TODO).
- Каталог Open Food Facts знает не все российские товары — для неизвестных кодов
  приложение предлагает ручное добавление с предзаполненным штрихкодом и сроком из кода.

## Документация

[ARCHITECTURE.md](ARCHITECTURE.md) · [ROADMAP.md](ROADMAP.md) ·
[PROJECT_STATE.md](PROJECT_STATE.md) · [THREAT_MODEL.md](THREAT_MODEL.md) ·
[PRIVACY.md](PRIVACY.md) · [PERFORMANCE.md](PERFORMANCE.md) · [TESTING.md](TESTING.md) ·
[CHANGELOG.md](CHANGELOG.md) · [TODO.md](TODO.md) · [ADR](docs/adr/)

## Конфиденциальность

Local-first, без регистрации, без рекламных SDK и трекеров. Подробнее — [PRIVACY.md](PRIVACY.md).
