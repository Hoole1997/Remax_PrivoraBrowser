/**
 * URL parameter helpers.
 */

function searchParams() {
  return new URLSearchParams(window.location.search);
}

export function getParam(name) {
  return searchParams().get(name) || "";
}

export function getQuery() {
  return getParam("q").trim();
}

/**
 * Query as it should appear in analytics events. Spaces collapsed to
 * `+` to match the original implementation.
 */
export function getQueryForAnalytics() {
  return getQuery().replace(/\s+/g, "+");
}

/**
 * Build the URL that related-search results should link back to.
 * Preserves `channel` and `network` so revenue attribution survives
 * across pages.
 */
export function buildResultsPageUrl() {
  const url = new URL("/search/", window.location.origin);
  const incoming = searchParams();
  ["channel", "network"].forEach((key) => {
    const value = incoming.get(key);
    if (value) url.searchParams.set(key, value);
  });
  // Trailing `?` with no params is ugly; URL handles that for us.
  return url.toString();
}

export function isInvalidTraffic() {
  return getParam("ivt") === "true";
}
