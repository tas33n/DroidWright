// Example 3: Scroll and Find
// This script demonstrates scrolling through a list to find a specific item

function run(ctx) {
  log("Searching for item");
  
  ctx.app.launch("com.example.app");
  ctx.device.sleep(2000);
  
  // Scroll and search for element
  const found = ctx.ui.waitFor(
    { text: "Target Item" },
    10000,  // 10 second timeout
    10,     // Max 10 scrolls
    { id: "list_container" }  // Scroll container
  );
  
  if (found) {
    ctx.ui.tap({ text: "Target Item" });
    ctx.device.sleep(1000);
    return { status: "ok", note: "Item found and tapped" };
  } else {
    return { status: "error", note: "Item not found" };
  }
}

