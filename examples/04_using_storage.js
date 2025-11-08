// Example 4: Using Storage
// This script demonstrates how to use persistent storage to track state between runs

function run(ctx) {
  // Get last run time
  const lastRun = ctx.storage.get("lastRunTime");
  const now = Date.now();
  
  if (lastRun) {
    const timeSince = now - parseInt(lastRun);
    log(`Time since last run: ${timeSince}ms`);
  }
  
  // Do automation...
  ctx.app.launch("com.example.app");
  ctx.device.sleep(2000);
  
  // Save current time
  ctx.storage.put("lastRunTime", now.toString());
  
  return { status: "ok" };
}

