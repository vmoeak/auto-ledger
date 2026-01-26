# Auto Ledger (Android)

Quick-capture expense logger for WeChat/Alipay payment success screens.

## MVP features
- Quick Settings tile: tap to capture current screen and parse via an OpenAI-compatible vision model.
- Floating overlay button: optional, tap to capture.
- Confirmation card before writing.
- Writes to a local CSV (picked via Storage Access Framework) so you can open it with Excel.

## How it works
1. Authorize screen capture (MediaProjection) once in the app.
2. Choose `ledger.csv` location (SAF document).
3. Tap tile / floating button on the payment success screen.
4. App captures screen, calls OpenAI-compatible `/v1/chat/completions`, expects **JSON-only** output, shows confirmation, then appends a row.

## Notes
- This is a debug-build-first project (no release signing).
- API key is stored using EncryptedSharedPreferences.
- MediaProjection permission is kept in memory only (MVP). You may need to re-authorize after app restart.
