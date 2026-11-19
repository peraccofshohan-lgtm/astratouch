# Roadmap

Ordered by user value per unit of risk. Nothing here is a promise; the
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) file explains why each item is
currently absent.

## 1.x — polish the shipped surface

- [ ] **Quick Settings tile** — start/stop the bubble and run a chosen macro from
      the shade. No new permission required.
- [ ] **On-device OCR** via ML Kit bundled text recognition (~20 MB APK growth),
      replacing the disabled "reserved" switch in the Experimental lab.
- [ ] **Timer scheduling with `AlarmManager`** so a timer fires while the app is
      dead, plus exact-alarm permission handling.
- [ ] **Widget** — one-tap macro runner.
- [ ] **Macro conditions expansion** — foreground app, time of day, battery level,
      charging state, screen state; currently the evaluator supports a smaller set.
- [ ] **Backup encryption** — optional passphrase (age/AES-GCM) for the JSON
      export.
- [ ] **Physical-device test matrix** — Pixel, Samsung One UI, Xiaomi HyperOS
      documented in COMPATIBILITY.md.
- [ ] **TalkBack audit** with a screen-reader user and published results.

## 1.2 — workflows people ask for

- [ ] **Macro import/export as a single file**, shareable as a `.astramacro` JSON.
- [ ] **Radial menu templates** — one-tap layouts ("media", "navigation", "work")
      that pre-fill slots.
- [ ] **Profile auto-switch by time window** in addition to foreground package.
- [ ] **Clipboard search and type filters** (URLs, phone numbers, code).
- [ ] **Per-app bubble behaviour** — hide the bubble in chosen packages.

## 2.0 — larger architecture

- [ ] **Multi-bubble** — several independently configured bubbles with different
      radial menus.
- [ ] **Automation triggers** — screen off/on, charger connected, app launched,
      time; v1 has manual macros only.
- [ ] **Localisation** — string extraction is done; needs translators. Bengali
      first (the author's locale), then Spanish, Hindi, Portuguese.
- [ ] **Wear OS companion** for timer/macro triggers.
- [ ] **Optional Play Store distribution** with the accessibility declaration
      package required by policy.

## Explicitly rejected, permanently

- Root, Xposed or Shizuku-only functionality.
- A full-screen invisible gesture-capture overlay.
- Any network permission, analytics SDK, account or cloud sync.
- Auto-clicking or automating another app's UI (click-fraud vector).
- Bundled scripting engines.

## How to influence this list

Open an issue describing the workflow you want, not the implementation. Real usage
reports outrank feature symmetry: "I want to screenshot and immediately annotate
without leaving my app" is more useful than "add more actions".
