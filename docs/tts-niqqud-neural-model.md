# TTS Hebrew Pronunciation — Full Neural Niqqud Model (Deferred)

## What this is

A deferred implementation option for improving Hebrew TTS pronunciation by injecting
niqqud (vowel diacritics) into text before sending it to Chirp3-HD. Instead of a
lookup table, this uses a full neural model that understands sentence context and
annotates every word correctly.

## Why it works

Chirp3-HD mispronounces Hebrew because the same spelling can have multiple readings
(e.g. "ספר" = book / barber / he-counted). Adding niqqud removes all ambiguity —
"סֵפֶר" can only be read one way.

## Architecture

```
User text
    │
    ▼
NiqqudResolver.annotate(text)          ← NEW (runs locally on device)
    │   - loads ONNX model from internal storage
    │   - tokenizes with Hebrew BERT tokenizer
    │   - runs inference (~100–300 ms)
    │   - outputs fully vocalized text
    ▼
cleanForTts()  →  buildSsml()  →  Google Cloud TTS (Chirp3-HD)
```

## Model

**Source:** Dicta niqqud model via [dicta-onnx](https://github.com/thewh1teagle/dicta-onnx)
**Format:** ONNX (needs INT8 quantization before shipping)

| Variant       | Size       | Accuracy |
|---------------|------------|----------|
| float32       | ~400 MB    | Best     |
| INT8 quantized| ~100 MB    | -1–2%    |

INT8 quantization is the right choice — 4× smaller, negligible quality loss.

## What gets added to the APK

| Component                        | Size added to APK |
|----------------------------------|-------------------|
| ONNX Runtime Android library     | +30–50 MB         |
| Hebrew BERT tokenizer vocab      | +1 MB             |
| Model itself                     | downloaded at runtime |

## First-launch download

The model is **not bundled** — it is downloaded on first launch:
- Show a progress screen: "מוריד שיפור הגייה — פעם אחת בלבד"
- Store in `context.filesDir` (internal storage, no permissions needed)
- Resume-on-failure support
- Fallback to homograph dictionary (see other doc) until download completes

**Download size:** ~100 MB (INT8 quantized, optionally gzip-compressed ~5–10% savings)
**Download time:** ~16s on 50 Mbps / up to 3 min on weak connection

## Implementation tasks

1. **Spike first** — validate model output quality on 20–30 sample sentences before full build
2. Get quantized ONNX model from `dicta-onnx` GitHub releases and verify INT8 quality
3. Add `com.microsoft.onnxruntime:onnxruntime-android` dependency
4. Port Hebrew BERT tokenizer to Kotlin (hardest part — no ready-made Android library)
5. Build `NiqqudResolver` class: tokenize → inference → decode → reconstruct string
6. Build `ModelDownloadManager`: first-launch download, progress, resume, fallback
7. Hook into `GoogleCloudTtsManager` before `buildSsml()`
8. Add first-launch UI (download progress screen or silent background download)

## Effort estimate

2–3 weeks. The tokenizer port is the most uncertain part — budget extra time there.

## Why it was deferred

- High implementation complexity
- The tokenizer has no ready-made Android/Kotlin library
- 100 MB first-launch download is acceptable but needs good UX
- The homograph dictionary approach (see `tts-homograph-dictionary.md`) covers most
  real-world cases with far less effort, and can be shipped first

## Decision trigger

Revisit this when the homograph dictionary misses enough cases that users notice.
Track mispronunciation reports — if the substitution map grows beyond ~100 entries
and still has gaps, the neural model becomes worth the investment.
