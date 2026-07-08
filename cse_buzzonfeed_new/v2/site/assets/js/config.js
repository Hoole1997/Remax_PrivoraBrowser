/**
 * Reads the inline `<script id="cse-config">` JSON block and exposes it
 * as a frozen object. Any missing field falls back to a safe default.
 *
 * `adTest` resolution order (URL > session > inline default):
 *   1. `?adtest=on|off|true|false|1|0` in the URL (also accepts `?env=dev`
 *      as an alias for `on`, `?env=prod` for `off`).
 *   2. Whatever was last seen via URL in this tab (sessionStorage).
 *   3. The value in the inline config block (default: "off").
 *
 * This way developers can flip into test ads with `?adtest=on` once
 * per tab and the choice persists during the session, without needing
 * to redeploy or rebuild.
 */

const DEFAULTS = Object.freeze({
  cseId: "",
  pubId: "",
  gaId: "",
  // "off" = real ads (production). "on" = AdSense test mode (no
  // revenue, no abuse signal). Toggle at runtime via ?adtest=on.
  adTest: "off",
  relatedSearchCount: 8,
  // Time after which we assume CSE failed to render and show the
  // error state.
  renderTimeoutMs: 8000,
});

const AD_TEST_STORAGE_KEY = "cse:adtest";

function readInlineConfig() {
  if (typeof window === "undefined") return {};
  if (window.__CSE_CONFIG__ && typeof window.__CSE_CONFIG__ === "object") {
    return window.__CSE_CONFIG__;
  }
  const node = document.getElementById("cse-config");
  if (!node) return {};
  try {
    return JSON.parse(node.textContent || "{}");
  } catch (_err) {
    return {};
  }
}

/** Returns "on"/"off" or null if the input doesn't look like a boolean. */
function normalizeAdTest(raw) {
  if (raw === undefined || raw === null) return null;
  const v = String(raw).trim().toLowerCase();
  if (v === "on" || v === "true" || v === "1" || v === "dev") return "on";
  if (v === "off" || v === "false" || v === "0" || v === "prod") return "off";
  return null;
}

function resolveAdTest(defaultValue) {
  if (typeof window === "undefined") return defaultValue;

  let params;
  try {
    params = new URLSearchParams(window.location.search);
  } catch (_) {
    params = null;
  }

  // 1. URL takes priority. Accept `adtest`, `env` (dev/prod alias).
  if (params) {
    const fromUrl =
      normalizeAdTest(params.get("adtest")) ||
      normalizeAdTest(params.get("env"));
    if (fromUrl) {
      try {
        window.sessionStorage.setItem(AD_TEST_STORAGE_KEY, fromUrl);
      } catch (_) {}
      return fromUrl;
    }
  }

  // 2. Whatever was last seen in this tab.
  try {
    const stored = window.sessionStorage.getItem(AD_TEST_STORAGE_KEY);
    const normalized = normalizeAdTest(stored);
    if (normalized) return normalized;
  } catch (_) {}

  // 3. Inline default.
  return defaultValue;
}

const inline = Object.assign({}, DEFAULTS, readInlineConfig());
const merged = Object.assign({}, inline, {
  adTest: resolveAdTest(inline.adTest || DEFAULTS.adTest),
});

export const config = Object.freeze(merged);

/** True when running in AdSense test mode (no real revenue). */
export const isAdTestMode = config.adTest === "on";

/** CSE postMessage protocol prefixes (Google internal, observed). */
export const CSE_MESSAGE = Object.freeze({
  // Sent when the user clicks an ad in the results iframe.
  AD_CLICK_PREFIX: "FSXDC,.aCS:",
  // Sent when an ad iframe finishes its initial render.
  AD_RENDER_PREFIX: "FSXDC,irt:",
});

/** GA4 event names used by this page. Keep in sync with dashboards. */
export const EVENTS = Object.freeze({
  PAGE_VIEW: "searchresult_page_view",
  CSE_RENDERED: "cse_rendered",
  CSE_TIMEOUT: "cse_render_timeout",
  AD_LOADED: "ad_loaded",
  AD_CLICK: "ad_click",
  CONTENT_CLICK: "content_click",
  RS_LOADED: "rs_loaded",
});
