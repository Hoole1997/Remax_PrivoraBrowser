/**
 * Ad load / click tracking.
 *
 * Two signal sources, both deduped via `state.adLoadedReported`:
 *   1. postMessage from Google's ad iframe (`FSXDC,irt:` for render,
 *      `FSXDC,.aCS:` for click).
 *   2. MutationObserver fallback that watches `.gsc-wrapper` for any
 *      iframe gaining a non-zero size — useful when postMessage is
 *      delivered before our listener is attached.
 */

import { CSE_MESSAGE, EVENTS } from "./config.js";
import { track } from "./analytics.js";

const state = {
  adLoadedReported: false,
  observer: null,
};

function reportAdLoaded() {
  if (state.adLoadedReported) return;
  state.adLoadedReported = true;
  track(EVENTS.AD_LOADED);
  stopObserving();
}

function reportAdClick() {
  track(EVENTS.AD_CLICK);
}

function handleMessage(event) {
  const data = event && event.data;
  if (typeof data !== "string") return;
  if (data.startsWith(CSE_MESSAGE.AD_CLICK_PREFIX)) {
    reportAdClick();
  } else if (data.startsWith(CSE_MESSAGE.AD_RENDER_PREFIX)) {
    reportAdLoaded();
  }
}

function startObserving() {
  if (state.observer) return;
  const target = document.querySelector(".gsc-wrapper");
  if (!target) return;
  state.observer = new MutationObserver((mutations) => {
    if (state.adLoadedReported) {
      stopObserving();
      return;
    }
    for (const mutation of mutations) {
      const node = mutation.target;
      if (!node || node.nodeName !== "IFRAME") continue;
      const rect = node.getBoundingClientRect();
      if (rect.width > 0 || rect.height > 0) {
        reportAdLoaded();
        return;
      }
    }
  });
  state.observer.observe(target, {
    attributes: true,
    childList: true,
    subtree: true,
  });
}

function stopObserving() {
  if (!state.observer) return;
  state.observer.disconnect();
  state.observer = null;
}

export function initAdTracking() {
  window.addEventListener("message", handleMessage, false);
}

export function onCseRendered() {
  // Try to attach the observer once `.gsc-wrapper` exists.
  startObserving();
}
