# PoE2 Rune Checker

Оверлей цен для окна **«Runeshape Combinations»** в Path of Exile 2.
Читает результат каждой строки-рецепта через OCR, берёт актуальные цены с
[poe.ninja](https://poe.ninja/poe2) и показывает поверх игры стоимость каждой
комбинации — сразу видно, какую руну выгоднее собрать.

> Цены поверх игры, подсветка самой выгодной строки, переключение лиг.

---

## ⬇️ Скачать (готовая сборка)

Качай RAR со страницы [**Releases**](https://github.com/or1k/Poe2_rune_checker/releases/latest)
([прямая ссылка](https://github.com/or1k/Poe2_rune_checker/releases/download/v0.1.0/PoE2RuneChecker-v0.1.0-win64.rar)) —
распакуй и запусти `PoE2RuneChecker.exe`. **Java ставить не нужно**, всё вшито.

## 🎮 Как пользоваться

1. Запусти `PoE2RuneChecker.exe` — откроется небольшое окно управления (выбор лиги).
2. Зайди в игру, открой окно **«Runeshape Combinations»** — игра встаёт на паузу.
3. Поверх строк появятся цены в exalted; самая выгодная подсвечивается зелёным.
4. Закрыл окно рун — цены пропадают. Крестик окна сворачивает его в трей.

> Работает в режиме **Windowed Fullscreen / Borderless** (а на Win10/11 обычно и в
> «полноэкранном» благодаря Fullscreen Optimizations).

## ✨ Возможности

- 💰 Реальные цены с poe.ninja PoE2 — **все 13 категорий** (валюта, фрагменты, руны,
  soul cores, эссенции, идолы, lineage gems, verisium, abyss, breach, expedition,
  liquid emotions, omens) — ~595 предметов.
- 🔍 OCR с fuzzy-сопоставлением названий (терпит ошибки распознавания).
- 🏆 Подсветка самой выгодной строки, формат «итог (за штуку)».
- ⏸️ Прячет цены по баннеру «GAME PAUSED» при закрытии окна.
- 💾 Кэш цен на диск, обновление не чаще раза в 15 минут.
- 🔁 Переключение лиг (Runes of Aldur / HC / Standard) в окне или трее.
- 🖱️ Прозрачный click-through оверлей (клики проходят в игру).
- 📦 Самодостаточный `.exe` (вшитая JRE + JavaFX + Tesseract + tessdata).

## 🛠️ Стек

Java 23 · JavaFX (UI) · Swing/AWT (прозрачный оверлей) · Tesseract OCR (tess4j) ·
Jackson (JSON) · JNA (click-through) · jpackage (.exe).

## 🔨 Сборка из исходников

Нужны JDK 23 и Maven.

**Dev-запуск:**
```
run.bat            # или: mvn javafx:run
```

**Сборка .exe:** скачай JavaFX 23.0.1 jmods (Windows x64) с
[gluonhq](https://gluonhq.com/products/javafx/) и распакуй в
`tools/javafx-jmods-23.0.1/`, затем:
```
build-exe.bat
```
Результат — `dist/PoE2RuneChecker/PoE2RuneChecker.exe`.

> `tessdata/eng.traineddata` уже в репозитории; иконка генерится `java tools/IconGen.java`.

## ☕ Поддержать

Если тулза пригодилась — [donatello.to/Or1on4ik](https://donatello.to/Or1on4ik). Совершенно по желанию.

## ⚠️ Дисклеймер

Использует только публичные данные poe.ninja и распознавание экрана — **не читает
память игры и не автоматизирует действия**, что соответствует политике GGG в отношении
оверлеев (как Awakened PoE Trade). Используешь на свой риск.
