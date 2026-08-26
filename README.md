==================================================
SOURCE: ./README_v33.md
==================================================

# Video Player v33 — Audit Follow-up

Implemented after the v32 audit:
- Library refreshes safely on resume when returning from management/player screens.
- Mini-player dismissal is session-stable for the dismissed video and will not immediately reappear on Home refresh. A newly played video can still surface it.
- PlaylistAdapter now uses DiffUtil + stable IDs instead of notifyDataSetChanged().
- Library scan status labels moved to resources for cleaner localization/maintenance.
- Existing v32 compile/resource fixes are retained.

Deferred intentionally for a dedicated migration pass:
- Full DB identity migration from filePath to MediaStore ID/URI.
- True incremental MediaStore reconciliation for very large libraries.
- Full PlayerActivity decomposition.


==================================================
SOURCE: ./README_v31.md
==================================================

# VideoPlayer v31 — Home Header Polish

## Changes
- Removed the redundant top Home screen search button.
- Bottom navigation Search remains the single clear entry point for search.
- Kept the Home overflow menu and media rescan functionality unchanged.
- Folder gesture fixes from v30 are retained.

## Expected Home header
Video Player title + library summary + More menu only.


==================================================
SOURCE: ./README_v27.md
==================================================

# VideoPlayer v27 — Functionality & Production Hardening

Base: v26 All Screens Premium.

## Functional polish
- Player skip duration now uses the Settings value for main rewind/forward buttons and double-tap gestures.
- Optional automatic Picture-in-Picture setting now controls home/back-to-background auto-PiP behavior.
- Default subtitles preference is applied whenever a new playback session starts.
- Default audio boost preference is applied and preserved across player recreation.
- Audio boost state is saved/restored with PlayerActivity state.
- Existing queue, repeat, shuffle, bookmarks, per-video memory, folder/video management, batch operations, library scanning and premium UI remain intact.

## Validation
- XML resources parsed successfully.
- Java source brace/structure checks passed.
- Archive integrity verified.
- Gradle/Android SDK toolchain is not available in this environment, so assembleDebug could not be executed here.


==================================================
SOURCE: ./README_v28.md
==================================================

# VideoPlayer v28 – Library & Player Control Polish

## Fixes
- Library folder list now renders directly inside the main NestedScrollView for reliable measurement across Android devices.
- Folder rows retain open, long-press and more-menu actions.
- Up Next is hidden during normal playback and appears only during the final 10 seconds when a queue item exists, with a live countdown.
- Added explicit release/back and More icon contrast/background so player top controls stay visible in light/dark device themes.
- Player advanced controls bottom sheet redesigned with drag handle, title/subtitle, close action, icon-led rows, selected-state check marks and adaptive list height.
- Option list selection is resilient to RecyclerView position invalidation.
- No new dependencies.

Validation performed: XML parse, duplicate string check, Java brace check, R.id reference audit (the only unresolved token is Material Components internal `design_bottom_sheet`).


==================================================
SOURCE: ./README_v32.md
==================================================

# Video Player v32 — Stability & Architecture Hardening

This release focuses on release-critical correctness without changing the core UI direction.

## Fixes
- Fixed `OptionAdapter` variable shadowing compile error.
- Hardened `VideoAdapter` DiffUtil content comparison so metadata changes rebind correctly.
- Made folder rename/delete RecoverableSecurityException flows resume the original operation after Android authorization.
- Made batch move/delete resume from the failed item instead of repeating completed items.
- Made bookmark migration conflict-safe with `INSERT OR IGNORE` + cleanup.
- Removed duplicate playlist menu entries and duplicate RecyclerView cache configuration.
- Cleared pending authorized operations and selection state on activity destruction.
- Kept AGP 7.4.2 / Gradle 7.4.2 compatibility.


==================================================
SOURCE: ./README_v26.md
==================================================

# Video Player v26 — Premium Design System Pass

Updated all primary screens to a consistent premium production UI language:
- Home
- Library
- Search
- Player surfaces
- Playlist detail
- Settings
- Video/folder/playlist list rows
- Mini player
- Empty and utility states

Key changes: unified spacing and touch targets, premium card surfaces, consistent chips, floating bottom navigation surface, cleaner headers, more intentional list/card hierarchy, theme-aware navigation tint, and stronger visual consistency without changing core behavior.

Source/resource validation performed locally; AndroidIDE/Gradle runtime build was not available in this environment.


==================================================
SOURCE: ./README_FINAL.md
==================================================

# Video Player — Final Production Build

Package: `com.reelixy.videoplayer`
Version: `1.0.0` (versionCode 100)
Gradle: `7.4.2`
Android Gradle Plugin: `7.4.2`

This final build keeps the v33 functionality and applies the final release polish:
- Requested package/application ID migration to `com.reelixy.videoplayer`
- Consistent package declarations/imports and custom-view XML reference migration
- Final localized player status/control strings
- Clean resource/string audit
- Release metadata set to 1.0.0
- Existing v32/v33 stability, library, management, playlist, player and UI work retained

Build in AndroidIDE with `:app:assembleDebug`.


==================================================
SOURCE: ./README.md
==================================================

# VideoPlayer v19 — Production Hardening

Based on v18 Library + Settings.

## Changes
- Database v3 with playback-history and playlist indexes.
- Unique playlist membership index; duplicate playlist inserts are ignored safely.
- Search Grid/List toggle now uses explicit UI state and is safe on empty results.
- Share chooser preserves the read URI grant.
- First launch respects the Android system theme; Light/Dark remain user-selectable.
- Existing v18 Library scan, Settings, Queue, bookmarks, per-video preferences, player gestures and Home polish are retained.

## Validation
- XML parsing: passed.
- Java brace/source balance: passed.
- Android Gradle wrapper is not included in this source package, so APK compilation must be run in AndroidIDE.
