# Архитектура

## Обзор

EatBefore построен как **local-first** приложение с чистым разделением слоёв внутри
одного Gradle-модуля (`:app`). Пакетное разделение выбрано вместо многомодульности,
чтобы держать сборку быстрой на этапе Foundation; границы пакетов соответствуют будущим
модулям, поэтому выделение модулей позже не потребует переписывания.

```
UI (Compose, ViewModel)  ->  Domain (use cases, интерфейсы)  ->  Data (репозитории, Room)
        зависит от                    ничего не знает                реализует интерфейсы
     Android/Compose                  об Android                     домена
```

## Слои

### domain (без Android)
- **Модели**: `Product`, `InventoryBatch`, `InventoryEvent`, `StorageLocation`,
  `ShoppingListItem`, `InventoryItem`, enum-ы, `ExpiryStatus`.
- **Интерфейсы репозиториев**: `ProductRepository`, `InventoryRepository`,
  `HistoryRepository`, `StorageLocationRepository`.
- **Провайдеры (точки расширения)**: `ProductCatalogProvider`, `ExpiryDateOcrProvider`.
- **Use cases**: единицы бизнес-логики, чистые и тестируемые. Каждая операция изменения
  запаса возвращает результат и **обязательно порождает `InventoryEvent`**.

Ключевой инвариант: *«карточка продукта»* (`Product`) отделена от *«партии запаса»*
(`InventoryBatch`). Один продукт → много партий с разными сроками и местами.

### data
- Реализации репозиториев (`*Impl`) поверх Room DAO.
- **Мапперы** entity↔domain — домен не видит Room-типов.
- Мутации, меняющие запас, применяются **атомарно вместе с событием истории**
  (`addBatchWithEvent`, `updateBatchWithEvent` через `db.withTransaction`), поэтому
  аудит-лог не может рассинхронизироваться с данными.
- Fake-провайдеры (`FakeProductCatalogProvider`, `NoopExpiryDateOcrProvider`) — рабочие
  заглушки до подключения реальных сервисов.

### core
- `core/database` — Room: entity, DAO, `Converters`, `EatBeforeDatabase`, `Migrations`,
  seed мест хранения через `RoomDatabase.Callback`. Экспорт схем в `app/schemas/`.
- `core/datastore` — `UserPreferencesRepository` (онбординг, порог «скоро истекает»,
  подробный режим количества).
- `core/designsystem` — тема M3 (светлая/тёмная + dynamic color), `StatusBadge`,
  общие состояния, форматтеры.
- `core/common` — `AppClock` (тестируемое время), диспетчеры, `InputValidator`.

### Миграции базы (обязательная процедура)

Данные пользователя живут только на устройстве, поэтому **потеря схемы = потеря всего**.
`fallbackToDestructiveMigration` намеренно не включён: несовместимая схема уронит
приложение, но не сотрёт данные.

Порядок при любом изменении схемы:
1. Поменять entity и поднять `EatBeforeDatabase.VERSION` — KSP экспортирует
   `app/schemas/<version>.json` (файл **коммитится**, это контракт).
2. Добавить `Migration(n, n+1)` в `ALL_MIGRATIONS` (`core/database/Migrations.kt`).
3. Добавить тест в `androidTest/.../MigrationTest.kt`: создать БД предыдущей версии,
   прогнать миграцию, проверить, что данные на месте.

Ближайшее изменение схемы — uuid и deviceId для совместного доступа
([ADR-0004](docs/adr/0004-household-sharing.md)).

### feature / ui / navigation
- Каждый экран = `Screen` (Compose) + `ViewModel` (Hilt, `StateFlow<UiState>`).
- **Бизнес-логики в Compose нет** — только отрисовка состояния и вызовы intent-методов VM.
- `navigation` — маршруты, нижняя навигация, единый `NavHost`.
- `ui/EatBeforeApp` — корневой каркас: `RootViewModel` решает онбординг vs главная.

## Управление состоянием

MVVM с однонаправленным потоком: `ViewModel` собирает данные из Flow-репозиториев,
маппит в `UiState` через `combine`/`stateIn` (`WhileSubscribed(5s)`), UI подписывается
через `collectAsStateWithLifecycle`. Intent-методы VM инкапсулируют действия.

## Внедрение зависимостей (Hilt)

- `CoreModule` — `AppClock`, диспетчеры.
- `DatabaseModule` — Room-база (+ seed-колбэк), DAO.
- `DataStoreModule` — Preferences DataStore.
- `RepositoryModule` — привязки интерфейс→реализация репозиториев и провайдеров.
- `WorkManager` инициализируется через `Configuration.Provider` в `EatBeforeApplication`
  (задел под уведомления).

## Целостность данных

- **Soft-delete**: использованные/выброшенные партии не удаляются физически
  (`deletedAt` + терминальный статус) — история и восстановление продолжают работать.
- **История append-only**: ошибки исправляются компенсирующими событиями (RESTORED,
  обратный MOVED и т.п.), а не редактированием прошлых записей.
- **Миграции**: фреймворк подключён (`ALL_MIGRATIONS`), схема версии 1 экспортируется;
  при изменении схемы добавляется `Migration` (без разрушающего fallback).
- **Индексы** Room на barcode, product_id, storage_location_id, expiration_date, status.

## Точки расширения (заложены, реализуются по roadmap)

| Возможность            | Точка расширения                        |
|------------------------|-----------------------------------------|
| Внешний каталог товаров| `ProductCatalogProvider`                |
| OCR срока годности     | `ExpiryDateOcrProvider`                 |
| Сканер                 | CameraX + ML Kit (feature/scanner)      |
| Уведомления            | WorkManager (`Configuration.Provider`)  |
| Облачная синхронизация | репозитории за интерфейсами (P2)        |

Архитектурные решения зафиксированы в [docs/adr/](docs/adr/).
