/**
 * Thin wrapper over `gtag`. Drops events silently if gtag is missing
 * (script blocked, offline, etc).
 */

import { getParam, getQueryForAnalytics } from "./params.js";

function dropEmpty(obj) {
  const out = {};
  for (const key in obj) {
    if (obj[key] !== "" && obj[key] !== null && obj[key] !== undefined) {
      out[key] = obj[key];
    }
  }
  return out;
}

export function track(eventName, extra = {}) {
  if (typeof window === "undefined" || typeof window.gtag !== "function") {
    return;
  }
  const payload = dropEmpty({
    keyword: getQueryForAnalytics(),
    channel: getParam("channel") || "organic",
    network: getParam("network"),
    ...extra,
  });
  try {
    window.gtag("event", eventName, payload);
  } catch (_err) {
    /* never let analytics break the page */
  }
}
