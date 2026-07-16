# Baseline Profile

Приложение целиком на Compose, а Compose при первом запуске интерпретируется — пока ART
не скомпилирует горячий код, запуск заметно медленнее. Baseline Profile — это список
классов и методов, которые ART компилирует заранее, **сразу при установке**.

Профиль лежит в `app/src/release/generated/baselineProfiles/` и **коммитится**:

| Файл | Что делает |
|---|---|
| `baseline-prof.txt` | AOT-компиляция перечисленных методов при установке |
| `startup-prof.txt` | R8 кладёт эти классы рядом в dex — меньше обращений к диску при старте |

Оба попадают в release-APK; на устройстве их ставит `androidx.profileinstaller`
(на Android 7–8 своего установщика в системе нет, поэтому библиотека обязательна).

## Как перегенерировать

Нужен **rooted**-эмулятор: образ `google_apis`, а **не** `google_apis_playstore` —
на Play-образах нет root, и генерация молча не соберёт данные.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
emulator -avd eatbefore_test -no-window -gpu swiftshader_indirect -no-snapshot &
adb wait-for-device && adb root

./gradlew :app:generateBaselineProfile
```

Задача сама собирает вариант `nonMinifiedRelease` (release без R8 — иначе имена методов
в профиле не совпали бы с реальными), гоняет сценарий из
`baselineprofile/src/main/java/.../BaselineProfileGenerator.kt` и кладёт результат в `:app`.
Занимает ~6–7 минут.

**Перегенерировать нужно** после заметных изменений в стартовом пути (навигация, темы,
DI-граф, экран «Главная») и при обновлении Compose. Устаревший профиль не ломает
приложение — он просто перестаёт покрывать новый код.

## Сценарий

`BaselineProfileGenerator` содержит два теста:

- `startup` — только запуск, помечен `includeInStartupProfile = true`;
- `journey` — прохождение по вкладкам (Запасы → Покупки → Ещё → Главная) со скроллом.

Сканер намеренно исключён: он требует разрешение на камеру, а системный диалог остановил
бы прогон.

UI Automator не видит семантику Compose, а искать по видимому тексту нельзя — на
англоязычном устройстве подписи другие. Поэтому в `EatBeforeApp` включён
`testTagsAsResourceId`, а у кнопок нижней навигации есть теги `nav_home`,
`nav_inventory` и т.д. **Если менять эти теги — чинить и генератор.**

## Замер эффекта

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Флаг `suppressErrors=EMULATOR` нужен потому, что macrobenchmark **по умолчанию
отказывается** мерить на эмуляторе — и правильно делает: цифры там недостоверны.

Замер на `eatbefore_test` (API 35, x86_64), `timeToInitialDisplayMs`, 10 итераций:

| | медиана | худший случай |
|---|---|---|
| без профиля | 494 мс | 626 мс |
| с профилем | 484 мс | 535 мс |

То есть медиана почти не изменилась (~2%), а худший случай стал лучше на ~15%. Так и
должно быть: эмулятор работает на x86-хосте с быстрым диском и сглаживает как раз те
издержки, которые профиль убирает. **Реальные цифры даёт только физическое устройство** —
там выигрыш при холодном старте обычно 15–30%.
