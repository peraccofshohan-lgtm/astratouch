# Database

AstraTouch persists structured state in a single Room (SQLite) database,
`astratouch.db`, alongside a small DataStore for scalar preferences.

- Database class: `app/src/main/java/com/astratouch/app/data/local/AstraDatabase.kt`
- Entities: `data/local/entity/Entities.kt`
- DAOs: `data/local/dao/Daos.kt`
- Type converters: `data/local/Converters.kt`
- Migrations: `data/local/AstraDatabase.kt` (`MIGRATION_1_2`)
- Exported schemas: `app/schemas/` (committed)
- Current version: **2**

## Design principles

1. **Everything is profile-scoped.** Profiles are the top-level isolation
   boundary; bubble config, radial config, radial items, gesture mappings and
   macros all reference a profile id. Switching profiles swaps the whole
   configuration atomically.
2. **No network, no secrets.** Clipboard content is stored only after
   `SensitiveContentFilter` approval; nothing is encrypted (there is no key
   material to protect and no network to leak it to).
3. **Cascades for owned rows.** Radial items and macro steps are deleted with
   their parent via `ForeignKey.CASCADE`, so no orphan rows accumulate.
4. **Ordering is explicit.** `sort_order` / `step_order` / `slot` columns avoid
   relying on rowid ordering.

## Tables

| Table | Purpose | Key columns |
| --- | --- | --- |
| `profiles` | Profile definitions and active flag | `id` PK, `type`, `is_active`, `auto_switch_package`, `sort_order` |
| `bubble_configs` | Bubble appearance and behaviour per profile | `profile_id`, `size_dp`, `shape`, `opacity`, `position_x/y`, `snap_mode`, `dock_side`, `auto_hide_ms` |
| `radial_configs` | Radial menu geometry per profile | `profile_id`, `layout`, `radius_dp`, `item_size_dp`, `background_*`, `accent` |
| `radial_items` | One row per menu slot | `radial_config_id` FK→config, unique (`radial_config_id`,`page`,`slot`), `action_id`, `is_favorite` |
| `gesture_mappings` | Gesture → action per profile | unique (`profile_id`,`gesture`), `action_id`, `sensitivity` |
| `macros` | Macro definitions | `profile_id`, `is_enabled`, `timeout_ms`, `last_run_status` |
| `macro_steps` | Ordered steps | `macro_id` FK→macros, `step_order`, `type`, `action_id`, `delay_ms` |
| `macro_executions` | Run history | `macro_id`, `started_at`, `finished_at`, `status`, `detail` |
| `theme_configs` | Saved themes | `preset_id`, colour columns, `is_dark`, `corner_radius_dp` |
| `favorite_apps` | Launcher state | `package_name` PK, `is_favorite`, `group_name`, `launch_count` |
| `notes` | Quick notes | autoincrement `id`, `title`, `body`, `is_pinned` |
| `clipboard_records` | Local clipboard history | autoincrement `id`, `content`, `is_sensitive_redacted`, `char_count` |
| `recent_actions` | Frequency ranking for "recent" | `action_id` PK unique, `used_at`, `use_count` |
| `timers` | Timers and stopwatch state | `duration_ms`, `remaining_ms`, `is_running`, `ends_at_epoch_ms` |

## Enums are stored as strings

`type`, `shape`, `snap_mode`, `dock_side`, `layout`, `animation_style`, `status`,
`category` and `gesture` are the enum `name` values. This keeps the schema
readable and lets a future enum value be added without a migration — unknown
values are mapped back to a documented default by `mapper/Mappers.kt`.

## Indices

- `bubble_configs(profile_id)`, `macros(profile_id)`, `macro_steps(macro_id)`,
  `macro_executions(macro_id)`, `radial_items(radial_config_id)` — foreign-key and
  filter scans.
- `radial_items(radial_config_id, page, slot)` **unique** — prevents two actions
  occupying the same menu slot.
- `gesture_mappings(profile_id, gesture)` **unique** — one action per gesture.
- `recent_actions(action_id)` **unique** — upsert instead of duplicates.

## Migration 1 → 2

v1 shipped profiles, bubble configs, radial configs and macro tables. v2 adds:

- `timers` table (timer/stopwatch persistence),
- `recent_actions.use_count` (frequency ranking),
- `favorite_apps.group_name` (launcher groups).

`MIGRATION_1_2` uses `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE … ADD COLUMN …
NOT NULL DEFAULT …` so it is idempotent and safe on partially-migrated data.

`MigrationTest` (instrumentation) builds a v1 database by hand, applies the
migration, then calls `runMigrationsAndValidate(..., 2, true)`. It asserts rows
survive and that the new columns carry the documented defaults.

## Destructive fallback

`fallbackToDestructiveMigrationOnDowngrade` is configured (`buildInMemory` /
`buildFile` helpers) so a user who side-loads an older build does not hit a hard
crash loop. **Upgrades are never destructive** — that path only exists for
downgrades, and it is called out in the release notes when relevant.

## Accessing the database

Repositories in `data/repository/` are the only callers of DAOs. They map entities
to domain models in `data/mapper/Mappers.kt`; ViewModels never see an entity.

```kotlin
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val bubbleDao: BubbleConfigDao,
) {
    fun activeProfile(): Flow<Profile?> = dao.observeActive().map { it?.toDomain() }
}
```

## Schema changes checklist

1. Edit the entity.
2. Bump `AstraDatabase.VERSION`.
3. Write `MIGRATION_{n}_{n+1}`.
4. Register it in `.addMigrations(...)`.
5. Build once so KSP exports `app/schemas/com.astratouch.app.data.local.AstraDatabase/{n+1}.json`.
6. Commit the new schema JSON.
7. Add a migration test case.
8. Note the change in `CHANGELOG.md` and, if user-visible, in
   [DATABASE.md](DATABASE.md).

## Backup interaction

Room tables are what `BackupManager` serialises into `BackupEnvelope`. It exports
profiles, bubble configs, radial configs, radial items, gesture mappings, macros,
macro steps and themes. Clipboard history, notes, screenshots, recordings, recent
actions, macro run history and timers are **intentionally excluded** — they are
either ephemeral or sensitive.
