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

## Что стоит на этих версиях сейчас

Kotlin 2.3.21, KSP 2.3.10, Hilt 2.58, AGP 8.10.1, compileSdk 36, Gradle 8.11.1,
Compose BOM 2026.06.01, Room 2.8.4, WorkManager 2.11.2, CameraX 1.6.1,
spotless 8.8.0.

## Когда возвращаться к AGP 9

Когда **и** `androidx.baselineprofile`, **и** detekt выпустят поддержку AGP 9.
Тогда:

1. убрать блокировки из `.github/dependabot.yml`;
2. убрать плагин `org.jetbrains.kotlin.android` из модулей — AGP 9 подключает
   Kotlin сам;
3. поднять Gradle до 9.x, compileSdk до 37;
4. проверять `:app:assembleDebug`, а не только компиляцию Kotlin.
