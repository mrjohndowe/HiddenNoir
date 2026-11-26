# Changelog

## 0.4.1-dev

- Clarify draft editing instructions to preserve existing wording and meaning without adding intimate details.
- Separate review instructions from draft data and preserve existing tone, sender perspective, and shared references.
- Reduce randomness for text reviews to favor faithful edits.
- Reject dropped abbreviations and near-copy rewrites; provide a clearly labeled conservative light edit if model review fails.
- Remember the 18+ adult confirmation across drafts and app restarts; it can be turned off at any time.
- Replace the keyboard's one-tap 18+ assertion with a confirmation toggle that must be on before explicit replies are generated.

## 0.4.0-dev

- Read visible text from the app used with HiddenNoir Keyboard through the Screen control.
- Recognize image text from an on-demand screenshot using bundled, offline text recognition on Android 11 and newer.
- Preview and choose the text to answer before generating local replies; retain Google Messages speaker labels when available.
- Exclude the keyboard from screenshot recognition and keep screenshots in memory only.
