// Example 7: Dynamic Selectors
// This script demonstrates building selectors dynamically and reusable functions

function run(ctx) {
  log("Using dynamic selectors");
  
  ctx.app.launch("com.example.app");
  ctx.device.sleep(2000);
  
  // Helper function to find button by text
  function findButtonByText(text) {
    return ctx.ui.find({ text: text, clickable: "true" });
  }
  
  // Helper function to tap if exists
  function safeTap(selector) {
    if (ctx.ui.exists(selector)) {
      ctx.ui.tap(selector);
      ctx.device.sleep(1000);
      return true;
    }
    return false;
  }
  
  // Use helper functions
  const loginBtn = findButtonByText("Login");
  if (loginBtn) {
    safeTap({ text: "Login" });
  }
  
  // Build selector dynamically
  const buttonTypes = ["Submit", "Continue", "Next"];
  for (const type of buttonTypes) {
    if (safeTap({ text: type })) {
      log(`Tapped ${type} button`);
      break;
    }
  }
  
  return { status: "ok" };
}

