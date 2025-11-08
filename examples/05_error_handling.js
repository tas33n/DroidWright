// Example 5: Error Handling
// This script demonstrates proper error handling and validation

function run(ctx) {
  try {
    log("Starting automation");
    
    // Launch app
    if (!ctx.app.launch("com.example.app")) {
      return { status: "error", note: "Failed to launch app" };
    }
    
    ctx.device.sleep(3000);
    
    // Check if app loaded
    const currentApp = ctx.app.getPackageName();
    if (currentApp !== "com.example.app") {
      return { status: "error", note: "App did not launch correctly" };
    }
    
    // Wait for main screen
    if (!ctx.ui.waitFor({ text: "Main" }, 5000)) {
      return { status: "error", note: "Main screen not found" };
    }
    
    // Perform actions
    ctx.ui.tap({ text: "Button" });
    ctx.device.sleep(1000);
    
    return { status: "ok", note: "Automation completed" };
    
  } catch (error) {
    log(`Error: ${error.message}`);
    return { status: "error", note: error.message };
  }
}

