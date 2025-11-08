// Example 6: AI-Powered Automation
// This script demonstrates how to integrate Google Gemini AI for intelligent automation
// Requires: Google Gemini API key

function run(ctx) {
  log("Starting AI-powered automation");
  
  const YOUR_GEMINI_API_KEY = "YOUR_API_KEY_HERE"; // IMPORTANT: Add your API key
  if (YOUR_GEMINI_API_KEY === "YOUR_API_KEY_HERE") {
    log("ERROR: Please add your Gemini API Key to the script.");
    return { status: "error", note: "Missing API Key" };
  }
  
  // Launch target app
  const TARGET_APP = "com.instagram.android";
  ctx.app.launch(TARGET_APP);
  ctx.device.sleep(3000);
  
  // Define task for AI
  const task = "Find and like the first post in the feed";
  
  try {
    const agentActions = callAgent(ctx, task, YOUR_GEMINI_API_KEY);
    log(`AI responded with ${agentActions.length} actions.`);
    
    // Execute AI-generated actions
    for (const action of agentActions) {
      executeAction(ctx, action);
    }
    
    return { status: "ok", note: "AI automation completed" };
  } catch (error) {
    log(`AI agent failed: ${error.message}`);
    return { status: "error", note: error.message };
  }
}

function callAgent(ctx, userTask, apiKey) {
  const SYSTEM_PROMPT = `You are an expert Android automation assistant. 
Generate a JSON array of actions to complete the task. 
Available actions: ui.tap, ui.longTap, ui.setText, ui.scroll, ui.swipe, ui.waitFor, device.press, device.sleep, log.`;
  
  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=${apiKey}`;
  const response = ctx.network.fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{
        parts: [{ text: `${SYSTEM_PROMPT}\n\nTask: ${userTask}` }]
      }]
    })
  });
  
  const result = JSON.parse(response);
  const rawActionText = result?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!rawActionText) {
    throw new Error("No response from AI agent");
  }
  
  return JSON.parse(rawActionText);
}

function executeAction(ctx, action) {
  log(`Executing: ${action.action}`);
  switch (action.action) {
    case "ui.tap":
      ctx.ui.tap(action.selector || {});
      ctx.device.sleep(1000);
      break;
    case "ui.swipe":
      ctx.ui.swipe(action.x1, action.y1, action.x2, action.y2, action.duration || 400);
      ctx.device.sleep(2000);
      break;
    case "device.sleep":
      ctx.device.sleep(action.ms || 500);
      break;
    // Add more action handlers as needed
  }
}

