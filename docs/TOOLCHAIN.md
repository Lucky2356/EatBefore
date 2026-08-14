# Тулчейн: почему мы на AGP 8, а не 9

Коротко: **AGP 9 сейчас недостижим**, и это проверено сборкой, а не предположено.
Обновление до него потребовало бы выбросить Baseline Profile и отключить detekt —
цена выше выигрыша.

## Что проверено (23.07.2026)

Попытка перейти на AGP 9.3.0 + Gradle 9.6.1 + compileSdk 37 упирается в две
зависимости, которыми мы пользуемся:

| Компонент | Состояние | Итог |
|---|---|---|
| **AGP 9** | содержит **встроенный Kotlin** и падает, если применён плагин `org.jetbrains.kotlin.android` (`AgpWithBuiltInKotlinAppliedCheck`) | плагин надо убирать — само по себе решаемо |
| **androidx.baselineprofile** | даже 1.4.1 не знает AGP 9: `Module ':app' is not a supported android module` | **блокер**: пришлось бы удалить модуль `:baselineprofile` |
| **detekt** | последний релиз — 1.23.8, новее нет | **блокер**: статический анализ пришлось бы отключить |

Оба блокера — наша собственная инфраструктура качества и скорости. Переход
означал бы минус Baseline Profile и минус detekt ради номера версии.

## Потолок, до которого можно обновляться на AGP 8

Цепочка совместимости выяснена перебором:

- **Hilt 2.58** — последний, который работает с AGP 8. Начиная с **2.58.2** плагин
  требует AGP 9.0+ (`The Hilt Android Gradle plugin is only compatible with ...`).
- **Kotlin 2.3.x** — потолок для Hilt 2.58: он читает метаданные Kotlin до версии
  2.3.0, а Kotlin 2.4 пишет 2.4.0 (`Provided Metadata instance has version 2.4.0,
  while maximum supported version is 2.3.0`).
- **compileSdk 37** требуют: `androidx.hilt:hilt-navigation-compose` 1.4.0,
  `androidx.core:core(-ktx)` 1.19.0 и `androidx.lifecycle` 2.11.0 — последние две
  дополнительно просят AGP 9.1+. Потолки на AGP 8: core **1.18.0**, lifecycle
  **2.10.0**, hilt-navigation-compose **1.2.0**.

Проверять требование можно, не собирая проект, — в AAR лежит
`META-INF/com/android/build/gradle/aar-metadata.properties` с `minCompileSdk`
и `minAgpVersion`.

Важная тонкость при проверке: `:app:compileDebugKotlin` **не** доходит до обработки
Hilt и проходит даже с несовместимой парой. Проверять надо задачей, которая
запускает `hiltJavaCompile` — например `:app:assembleDebug`.

## Перепроверка 15.08.2026

Проверялось сборкой, а не чтением списков версий.

| Обновление | Итог |
|---|---|
| **Gradle 8.11.1 → 9.6.1** | **взято.** AGP 8.10.1 с ним работает: собрались debug, release (R8), модуль `:baselineprofile`, прошли detekt, spotless, lint, 352 юнит- и 18 инструментальных тестов |
| **okhttp 4.12.0 → 5.4.0** | **взято.** Проверено не только сборкой: на эмуляторе реальный запрос к Open Food Facts по коду `4620017700531` вернул товар |
| **mockk 1.13.13 → 1.14.11** | **взято**, тесты зелёные |
| `androidx.core(-ktx)` 1.19.0 | **нельзя**: `checkDebugAarMetadata` требует AGP 9.1.0+ |
| AGP 9.3.1 | **нельзя**, причины прежние: detekt по-прежнему 1.23.8 (новее релизов нет), `androidx.baselineprofile` не знает AGP 9 |

То есть блокер один и тот же — detekt и Baseline Profile, — а Gradle 9 к нему,
вопреки ожиданию, не привязан: он поднялся отдельно и без потерь.
## Что стоит на этих версиях сейчас

Kotlin 2.3.21, KSP 2.3.10, Hilt 2.58, AGP 8.10.1, compileSdk 36, Gradle 9.6.1,
Compose BOM 2026.06.01, Room 2.8.4, WorkManager 2.11.2, CameraX 1.6.1,
okhttp 5.4.0, spotless 8.8.0, detekt 1.23.8, mockk 1.14.11.

## Когда возвращаться к AGP 9

Когда **и** `androidx.baselineprofile`, **и** detekt выпустят поддержку AGP 9.
Тогда:

1. убрать блокировки из `.github/dependabot.yml`;
2. убрать плагин `org.jetbrains.kotlin.android` из модулей — AGP 9 подключает
   Kotlin сам;
3. поднять compileSdk до 37 (Gradle 9 уже стоит);
4. проверять `:app:assembleDebug`, а не только компиляцию Kotlin.
