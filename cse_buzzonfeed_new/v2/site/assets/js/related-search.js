/**
 * AdSense for Search related-search block.
 *
 * If AdSense returns no inventory (common on test mode, new domains,
 * or low-commercial queries), we replace the empty container with a
 * "Continue on Google" link so the bottom of the page never feels
 * broken or empty.
 */

import { config, EVENTS } from "./config.js";
import { track } from "./analytics.js";
import {
  buildResultsPageUrl,
  getParam,
  getQuery,
  isInvalidTraffic,
} from "./params.js";

const state = {
  reported: false,
  loaded: false,
  fallbackTimer: null,
};

function renderFallback(reason) {
  const host = document.getElementById("related-search");
  if (!host) return;
  // Don't overwrite real ads.
  if (host.children.length > 0 && host.dataset.fallback !== "true") return;
  const q = getQuery();
  if (!q) return;
  const googleUrl =
    "https://www.google.com/search?q=" + encodeURIComponent(q);
  host.dataset.fallback = "true";
  host.innerHTML =
    '<div class="rs-fallback">' +
    '<span class="rs-fallback-title">More to explore</span>' +
    '<a class="rs-fallback-link" href="' +
    googleUrl +
    '" target="_blank" rel="noopener noreferrer">' +
    "Continue searching for &ldquo;" +
    escapeHtml(q) +
    "&rdquo; on Google" +
    '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" aria-hidden="true">' +
    '<path d="M5 12h14M13 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>' +
    "</svg>" +
    "</a>" +
    "</div>";
  track(EVENTS.RS_LOADED, { rs_fallback: reason });
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function handleLoaded(_a, hasResults) {
  state.loaded = true;
  if (state.fallbackTimer !== null) {
    window.clearTimeout(state.fallbackTimer);
    state.fallbackTimer = null;
  }
  if (!hasResults) {
    renderFallback("no-inventory");
    return;
  }
  if (state.reported) return;
  state.reported = true;
  track(EVENTS.RS_LOADED);
}

export function renderRelatedSearch() {
  const q = getQuery();
  if (!q) return;
  if (typeof window._googCsa !== "function") {
    renderFallback("no-csa");
    return;
  }

  // Replace the bootstrap stub with the real handler.
  if (window.__cseHooks) window.__cseHooks.rsLoaded = handleLoaded;

  const pageOptions = {
    pubId: config.pubId,
    relatedSearchTargeting: "query",
    resultsPageBaseUrl: buildResultsPageUrl(),
    query: q,
    resultsPageQueryParam: "q",
    adsafe: "low",
    linkTarget: "_blank",
    oe: "utf-8",
    ie: "utf-8",
    ivt: isInvalidTraffic(),
    adPage: 1,
    adLoadedCallback: window.RSLoaded,
    adtest: config.adTest,
  };

  const channel = getParam("channel");
  if (channel) pageOptions.channel = channel;

  const blockOptions = {
    container: "related-search",
    relatedSearches: config.relatedSearchCount,
  };

  window._googCsa("relatedsearch", pageOptions, blockOptions);

  // Watchdog: if AdSense never calls back (network failure, ad
  // blockers, no inventory), drop in our fallback after 6s.
  state.fallbackTimer = window.setTimeout(() => {
    if (state.loaded) return;
    renderFallback("timeout");
  }, 6000);
}
