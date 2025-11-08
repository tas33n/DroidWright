// Example 2: Form Filling
// This script demonstrates how to fill out a form with text input

function run(ctx) {
  log("Filling form");
  
  // Launch app
  ctx.app.launch("com.example.app");
  ctx.device.sleep(2000);
  
  // Fill form fields
  ctx.ui.setText({ id: "name_input" }, "John Doe");
  ctx.device.sleep(500);
  
  ctx.ui.setText({ id: "email_input" }, "john@example.com");
  ctx.device.sleep(500);
  
  // Submit
  ctx.ui.tap({ text: "Submit" });
  ctx.device.sleep(2000);
  
  // Check for success message
  if (ctx.ui.exists({ text: "Success" })) {
    return { status: "ok", note: "Form submitted successfully" };
  } else {
    return { status: "error", note: "Submission failed" };
  }
}

