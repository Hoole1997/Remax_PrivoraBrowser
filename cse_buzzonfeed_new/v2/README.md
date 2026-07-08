# Privora Browser Results Page (v2)

Refactored version of the static results page that powers
`https://cse.buzzonfeed.com/search/`. The Android app
(`DefaultSearchEngines.kt`) opens this URL with `?q={searchTerms}`.

The original implementation lives next to this folder and is left
untouched for reference.

## What this page does

1. Reads URL parameters (`q`, `channel`, `network`, `ivt`).
2. Renders Google Programmable Search Engine (CSE) results into a
   styled card.
3. Renders an AdSense for Search related-search block.
4. Reports lightweight analytics events to GA4.

## Folder layout

```
v2/
├── search/
│   └── index.html              entry point (also references config block)
├── site/
│   └── assets/
│       ├── css/
│       │   └── cse.css         design tokens + layout + CSE overrides
│       └── js/
│           ├── config.js       reads inline JSON config + constants
│           ├── params.js       URL param helpers
│           ├── analytics.js    gtag wrapper + event names
│           ├── ads.js          ad rendered/clicked tracking
│           ├── cse.js          CSE callbacks + render watchdog
│           ├── related-search.js  AdSense for Search
│           └── main.js         entry point (ES module)
└── README.md
```

No build step. Modules are loaded natively by the browser.

## Configuration

All third-party IDs live in a single block in `search/index.html`:

```html
<script id="cse-config" type="application/json">
  {
    "cseId":  "10b2765dafb5242cb",
    "pubId":  "pub-5149440008061651",
    "gaId":   "G-1GD07SFJG8",
    "adTest": "on",
    "relatedSearchCount": 8,
    "renderTimeoutMs": 8000
  }
</script>
```

`adTest: "on"` keeps AdSense in **test mode** (no real revenue). Flip
to `"off"` only when releasing to production.

## Local preview

Static files. Any local server works, e.g.:

```
python3 -m http.server --directory cse_buzzonfeed_new/v2 8080
# then open http://localhost:8080/search/?q=hello
```

## Deploy

Copy the contents of `v2/` over the production root that serves
`cse.buzzonfeed.com`. The relative path `../site/assets/...` from
`search/index.html` matches the layout used by the live site.

## Notes for future work

- `adtest: "on"` is the dev default; remember to flip before launch.
- CSE postMessage protocol prefixes (`FSXDC,.aCS:`, `FSXDC,irt:`) are
  internal Google details. They have been stable for a while but are
  not contractual; revisit if ad tracking goes silent.
- Ad-loaded detection in the main results uses both the CSE
  `searchCallbacks.web.rendered` hook and a `MutationObserver` watching
  for ad iframes. Either path triggers a single deduped `ad_loaded`
  event.
