/**
 * Hooks into Google CSE rendering callbacks.
 *
 * The HTML bootstrap pre-installs `window.__gcse` with a forwarder
 * that calls `window.__cseHooks.web.rendered`. We swap that hook here
 * once the module graph is ready.
 */

import { config, EVENTS } from "./config.js";
import { track } from "./analytics.js";
import { onCseRendered } from "./ads.js";

const state = {
  rendered: false,
  timeoutId: null,
  onTimeout: null,
  onSuccess: null,
};

function bindResultClickTracking(resultElts) {
  if (!resultElts) return;
  for (const el of resultElts) {
    if (!el || el.__clickTracked) continue;
    el.__clickTracked = true;
    el.addEventListener("click", () => track(EVENTS.CONTENT_CLICK), {
      passive: true,
    });
  }
}

function handleRendered(gname, query, _promoElts, resultElts) {
  if (state.rendered) {
    // CSE may re-render on pagination. We still let ads observer
    // re-arm, but skip the success/timeout state machine.
    onCseRendered();
    bindResultClickTracking(resultElts);
    return;
  }
  state.rendered = true;
  if (state.timeoutId !== null) {
    window.clearTimeout(state.timeoutId);
    state.timeoutId = null;
  }
  onCseRendered();
  bindResultClickTracking(resultElts);
  track(EVENTS.CSE_RENDERED, {
    rendered_query: query,
    rendered_name: gname,
  });
  if (typeof state.onSuccess === "function") state.onSuccess();
}

/**
 * Install the rendered callback and start the watchdog timer.
 *
 * @param {{ onSuccess?: () => void, onTimeout?: () => void }} hooks
 */
export function initCse(hooks = {}) {
  state.onSuccess = hooks.onSuccess || null;
  state.onTimeout = hooks.onTimeout || null;

  // Replace the bootstrap stub with the real handler. Anything that
  // already came in via the stub was a no-op, so we don't lose data.
  if (window.__cseHooks && window.__cseHooks.web) {
    window.__cseHooks.web.rendered = handleRendered;
  }

  state.timeoutId = window.setTimeout(() => {
    if (state.rendered) return;
    track(EVENTS.CSE_TIMEOUT);
    if (typeof state.onTimeout === "function") state.onTimeout();
  }, config.renderTimeoutMs);
}
