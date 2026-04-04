# Yumode - i-mode/WAP style RSS News Aggregator for Feature Phones and Garaho / Keitai

[![yumode.png](https://i.postimg.cc/KzKqkRH4/yumode.png)](https://postimg.cc/N9c7ZfYw)
Android RSS feed aggregator for D-pad navigation. 

This is personal tool so websites in catalog and feed are hardcoded, you can change it by redacting source code.

OS req - Android 5.0 and higher.

Tested on Sharp 806SH. App made for D-pad, intended for use on feature phones/Garaho phones. Does not support large displays or touchscreens.

Buttons 0–9/* — quick shortcuts. You can open an article or menu item by pressing the corresponding number on the keyboard.

Yumode main page portal is inspired by Yahoo! Keitai's main page; other sites use general i-mode/WAP style layouts and cHTML-style design.

The UI is implemented using Custom Android Views (without XML layouts or Compose) and is fully optimized for keyboard navigation: D-pad, soft keys, OK/Back.
Screen states (home/site/article) are rendered programmatically, and transitions and loading are synchronized via an overlay layer to hide intermediate states when switching between categories and pages.

### Architecture
Controller-driven navigation and screen state. ViewModel + LiveData for feed and UI state. Repositories for network and cache access. Overlay layer for loading and page transitions

## Build & Run

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Min SDK 21 required. Tested on Sharp 806SH

---

## License

Polyform Noncommercial 

See license.txt

---

AI-assisted development. All decisions regarding branding, UI, and architecture, as well as the overall concept of this app, are mine. AI only helped with coding. Icons for the Connection tooltip were drawn by me.

This is my first app for Android, so it's messy.