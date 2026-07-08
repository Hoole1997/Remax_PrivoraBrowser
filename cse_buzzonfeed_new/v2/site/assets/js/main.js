/**
 * Page entry point. Wires up the search bar UI, decides which state
 * card to show, and kicks off CSE + related search when there's a
 * query.
 */

import { EVENTS, isAdTestMode } from "./config.js";
import { track } from "./analytics.js";
import { getQuery } from "./params.js";
import { initAdTracking } from "./ads.js";
import { initCse } from "./cse.js";
import { renderRelatedSearch } from "./related-search.js";

const dom = {
  form: document.getElementById("searchForm"),
  input: document.getElementById("searchInput"),
  clear: document.getElementById("searchClear"),
  emptyState: document.getElementById("emptyState"),
  errorState: document.getElementById("errorState"),
  retry: document.getElementById("retryButton"),
  resultsCard: document.getElementById("resultsCard"),
  loading: document.getElementById("resultsLoading"),
};

function show(el) {
  if (el) el.hidden = false;
}
function hide(el) {
  if (el) el.hidden = true;
}

function showState(name) {
  hide(dom.emptyState);
  hide(dom.errorState);
  hide(dom.resultsCard);
  if (name === "empty") show(dom.emptyState);
  if (name === "error") show(dom.errorState);
  if (name === "results") show(dom.resultsCard);
}

function mountTestBadge() {
  if (!isAdTestMode || document.getElementById("adTestBadge")) return;
  const badge = document.createElement("button");
  badge.id = "adTestBadge";
  badge.type = "button";
  badge.className = "test-badge";
  badge.title =
    "AdSense test mode is on. Click to switch back to production ads.";
  badge.innerHTML =
    '<span class="test-badge-dot" aria-hidden="true"></span>' +
    '<span class="test-badge-text">TEST ADS</span>';
  badge.addEventListener("click", () => {
    try {
      window.sessionStorage.removeItem("cse:adtest");
    } catch (_) {}
    const url = new URL(window.location.href);
    url.searchParams.set("adtest", "off");
    url.searchParams.delete("env");
    window.location.replace(url.toString());
  });
  document.body.appendChild(badge);
}

function syncSearchUi() {
  const q = getQuery();
  if (dom.input) dom.input.value = q;
  if (dom.clear) dom.clear.hidden = !q;
}

function bindSearchUi() {
  if (dom.form) {
    dom.form.addEventListener("submit", (e) => {
      e.preventDefault();
      const value = ((dom.input && dom.input.value) || "").trim();
      if (!value) return;
      const url = new URL(window.location.href);
      url.searchParams.set("q", value);
      // Strip any stale CSE hash from the previous query. The HTML
      // bootstrap will rebuild it from the new ?q= on the next load,
      // which forces CSE to render the divider-free <div> path.
      url.hash = "";
      window.location.assign(url.toString());
    });
  }

  if (dom.input) {
    dom.input.addEventListener("input", () => {
      if (dom.clear) dom.clear.hidden = dom.input.value.length === 0;
    });
  }

  if (dom.clear) {
    dom.clear.addEventListener("click", () => {
      if (!dom.input) return;
      dom.input.value = "";
      dom.input.focus();
      dom.clear.hidden = true;
    });
  }

  if (dom.retry) {
    dom.retry.addEventListener("click", () => {
      window.location.reload();
    });
  }
}

function start() {
  syncSearchUi();
  bindSearchUi();
  mountTestBadge();
  initAdTracking();

  const q = getQuery();
  if (!q) {
    showState("empty");
    return;
  }

  showState("results");
  initCse({
    onSuccess: () => {
      if (dom.loading) dom.loading.remove();
    },
    onTimeout: () => {
      showState("error");
    },
  });
  renderRelatedSearch();
  track(EVENTS.PAGE_VIEW);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", start, { once: true });
} else {
  start();
}
