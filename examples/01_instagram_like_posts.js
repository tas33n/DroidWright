// Example 1: Instagram Like Posts
// This script automates liking posts on Instagram

function run(ctx) {
  log("Starting Instagram like automation");
  
  // Launch Instagram
  const INSTAGRAM_PKG = "com.instagram.android";
  ctx.app.launch(INSTAGRAM_PKG);
  ctx.device.sleep(3000);
  
  // Wait for feed
  if (!ctx.ui.waitFor({ text: "Home" }, 5000)) {
    return { status: "error", note: "Feed not loaded" };
  }
  
  // Like 5 posts
  for (let i = 0; i < 5; i++) {
    log(`Liking post ${i + 1}`);
    
    // Try to find and tap like button
    if (ctx.ui.exists({ desc: "Like" })) {
      ctx.ui.tap({ desc: "Like" });
    } else if (ctx.ui.exists({ id: "row_feed_button_like" })) {
      ctx.ui.tap({ id: "row_feed_button_like" });
    }
    
    ctx.device.sleep(2000);
    
    // Scroll to next post
    const size = ctx.device.getScreenSize();
    ctx.ui.swipe(
      size.width / 2,
      size.height * 0.8,
      size.width / 2,
      size.height * 0.2,
      500
    );
    ctx.device.sleep(3000); // Wait for next post to load
  }
  
  log("Automation completed");
  return { status: "ok", note: "Liked 5 posts" };
}

