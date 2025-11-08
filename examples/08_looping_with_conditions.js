// Example 8: Looping with Conditions
// This script demonstrates looping with retry logic and conditions

function run(ctx) {
  log("Searching with retry logic");
  
  ctx.app.launch("com.example.app");
  ctx.device.sleep(2000);
  
  let attempts = 0;
  const maxAttempts = 10;
  const size = ctx.device.getScreenSize();
  
  while (attempts < maxAttempts) {
    // Check if target element exists
    if (ctx.ui.exists({ text: "Target" })) {
      ctx.ui.tap({ text: "Target" });
      ctx.device.sleep(1000);
      log(`Found target after ${attempts} attempts`);
      return { status: "ok", note: "Target found" };
    }
    
    // Scroll and try again
    ctx.ui.swipe(
      size.width / 2,
      size.height * 0.8,
      size.width / 2,
      size.height * 0.2,
      500
    );
    ctx.device.sleep(2000);
    attempts++;
    
    log(`Attempt ${attempts}/${maxAttempts}`);
  }
  
  return { status: "error", note: "Target not found after max attempts" };
}

