# TTS Hebrew Pronunciation — Homograph Dictionary + Heuristics

## What this is

A lightweight offline solution for improving Hebrew TTS pronunciation.
Before text is sent to Chirp3-HD, ambiguous Hebrew words (homographs) are
replaced with their niqqud-vocalized form so the TTS engine has no ambiguity.

**No ML, no download, no added latency.** Just a HashMap lookup + a few rules.

## Architecture

```
User text
    │
    ▼
cleanForTts()              strips markdown, emoji, bare URLs
    │
    ▼
HomographResolver.resolve() ← inserts niqqud for known ambiguous words
    │
    ▼
buildSsml()  →  Google Cloud TTS (Chirp3-HD)
```

## The dictionary

File: `app/src/main/res/raw/hebrew_homographs.json`

Each entry:

```json
{
  "bare":    "ספר",       ← unvocalized word as it appears in text
  "default": "סֵפֶר",    ← used when no heuristic fires (most common reading)
  "noun":    "סֵפֶר",    ← used when context suggests a noun
  "verb":    "סָפַר"     ← used when context suggests a verb
}
```

`noun` and `verb` can be `null` — the resolver falls back to `default`.

## The heuristics

Applied in priority order:

| Rule | Signal | → Reading |
|------|---------|-----------|
| 1 | Word has **ל** prefix (`לספר`) | verb (infinitive) |
| 2 | Word has **ה** prefix (`הספר`) | noun (definite article) |
| 3 | Word has **ב / מ** prefix (`בדרך`, `מספר`) | noun (prepositional) |
| 4 | Previous word is a personal pronoun (`הוא`, `אני`…) | verb |
| 5 | Previous word is **יש** or **אין** | noun (existential) |
| 6 | Previous word is a demonstrative (`זה`, `זו`, `זאת`…) | noun |
| 7 | Next word is **את** (direct-object marker) | verb |
| 8 | No signal fires | → `default` |

## How prefix detection works

Prefixes in Hebrew are single characters attached directly to the word
(no space). The resolver tries the longest known prefix first, then shorter
ones, and only accepts a match if the stripped root is in the dictionary:

```
"הספר" → strip "ה" → "ספר" in dict? yes → noun form → "הסֵפֶר"
"בדרך" → strip "ב" → "דרך" in dict? yes → noun form → "בדֶּרֶךְ"
"לכתוב" → "כתוב" not in dict → no match → returned unchanged
```

## Current dictionary (25 entries)

| Bare | Default | Noun | Verb |
|------|---------|------|------|
| ספר | סֵפֶר (book) | סֵפֶר | סָפַר (counted) |
| דבר | דָּבָר (thing) | דָּבָר | דִּבֵּר (spoke) |
| שנה | שָׁנָה (year) | שָׁנָה | שִׁנָּה (changed) |
| פתח | פֶּתַח (entrance) | פֶּתַח | פָּתַח (opened) |
| מלך | מֶלֶךְ (king) | מֶלֶךְ | מָלַךְ (reigned) |
| עלה | עָלָה (went up) | עָלֶה (leaf) | עָלָה |
| שר | שָׁר (sang) | שַׂר (minister) | שָׁר |
| צבע | צֶבַע (color) | צֶבַע | צָבַע (painted) |
| שמח | שָׂמֵחַ (happy) | שָׂמֵחַ | שִׂמַּח (made happy) |
| כתב | כָּתַב (wrote) | כֶּתֶב (script) | כָּתַב |
| אם | אִם (if) | אֵם (mother) | — |
| עם | עִם (with) | עַם (people) | — |
| חזה | חָזֶה (chest) | חָזֶה | חָזָה (foresaw) |
| עבר | עָבַר (passed) | עֵבֶר (side) | עָבַר |
| דרך | דֶּרֶךְ (road) | דֶּרֶךְ | דָּרַךְ (trod) |
| שמע | שָׁמַע (heard) | שֵׁמַע (fame) | שָׁמַע |
| כלה | כַּלָּה (bride) | כַּלָּה | כָּלָה (ended) |
| פנה | פָּנָה (turned) | פִּנָּה (corner) | פָּנָה |
| שבר | שֶׁבֶר (shard) | שֶׁבֶר | שָׁבַר (broke) |
| נגע | נָגַע (touched) | נֶגַע (blemish) | נָגַע |
| רגע | רֶגַע (moment) | רֶגַע | רָגַע (calmed) |
| אסף | אָסַף (gathered) | אֹסֶף (collection) | אָסַף |
| צמח | צֶמַח (plant) | צֶמַח | צָמַח (grew) |
| חדש | חָדָשׁ (new) | חָדָשׁ | חִדֵּשׁ (renewed) |
| מסר | מֶסֶר (message) | מֶסֶר | מָסַר (delivered) |

## How to add a new entry

When a user reports a mispronounced word:

1. Find the bare (unvocalized) spelling
2. Look up the two readings in a Hebrew dictionary (e.g. Morfix, Milog)
3. Add an entry to `hebrew_homographs.json`
4. Set `default` to the more common reading in the app's context
5. Set `noun`/`verb` for the alternate reading

The resolver picks up the new entry on next app launch (lazy-loaded once).

## Limitations

- Only handles single-word disambiguation — cannot resolve truly
  syntactic ambiguities that require full sentence parsing
- Heuristics fire on surface patterns; unusual sentence structures
  may get the wrong reading
- Niqqud strings should be verified by a native Hebrew speaker;
  see `docs/tts-niqqud-neural-model.md` for a future full-accuracy option
