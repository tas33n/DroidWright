// ==DroidScript==
// @id              share-intent-demo
// @name            Share Intent Demo
// @description     Demo that shows what DroidWright sees when content is shared to it, and echoes the shared URL to the clipboard.
// @author          tas33n
// @version         1.0.0
// @targetApp       *
// @url             https://github.com/tas33n/droidwright
// @created         2025-01-08
// @match           *://*/*
// @include         *
// ==/DroidScript==

/**
 * Share-triggered demo script.
 *
 * Share any link or text to DroidWright from another app and this script
 * runs. Routing is driven by the header rules above:
 *   - @match "*://*/*" selects any shared URL
 *   - @include "*" selects any shared text (links included)
 * The shared data is available in ctx.intent.
 */
function droidRun(ctx) {
  if (!ctx.intent) {
    return { status: "error", note: "No intent payload - run once through the share sheet" };
  }

  log("Share received");
  log(`- action: ${ctx.intent.action}`);
  log(`- mime: ${ctx.intent.mime}`);
  log(`- subject: ${ctx.intent.subject}`);
  log(`- text: ${ctx.intent.text}`);
  log(`- url: ${ctx.intent.url}`);
  log(`- referrer: ${ctx.intent.referrer}`);
  log(`- extras: ${JSON.stringify(ctx.intent.extras)}`);

  if (ctx.intent.url) {
    ctx.device.setClipboard(ctx.intent.url);
    ctx.device.showToast("Shared URL copied: " + ctx.intent.url);
  }

  return {
    status: "ok",
    note: `Received share with url=${ctx.intent.url}`
  };
}
