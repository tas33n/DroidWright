# DroidWright Script Writing Guide

Complete guide to writing automation scripts for DroidWright.

## Table of Contents

1. [Introduction](#introduction)
2. [Script Structure](#script-structure)
3. [Defining Target Apps](#defining-target-apps)
4. [Finding UI Elements](#finding-ui-elements)
5. [API Reference](#api-reference)
6. [Selectors](#selectors)
7. [Best Practices](#best-practices)
8. [Examples](#examples)
9. [Troubleshooting](#troubleshooting)

## Introduction

DroidWright scripts are written in JavaScript and executed in a QuickJS runtime. Each script must define a `droidRun(ctx)` function that receives a context object with all available APIs.

### Basic Script Template

```javascript
function droidRun(ctx) {
  // Your automation code here
  log("Script started");
  
  // Return result
  return { status: "ok", note: "Completed successfully" };
}
```

## Script Structure

### Required Function

Every script **must** export a `droidRun(ctx)` function:

```javascript
function droidRun(ctx) {
  // ctx contains all APIs:
  // - ctx.app: App control API
  // - ctx.device: Device control API
  // - ctx.ui: UI interaction API
  // - ctx.network: Network API
  // - ctx.storage: Storage API
  
  return { status: "ok" };
}
```

### Return Value

The `droidRun()` function should return an object with:
- `status`: `"ok"` for success, `"error"` for failure
- `note`: Optional message describing the result

```javascript
return { status: "ok", note: "Task completed" };
// or
return { status: "error", note: "Failed to find element" };
```

## Defining Target Apps

**Important**: You must define package names directly in your script. There is no profile system - keep it simple!

```javascript
function droidRun(ctx) {
  // Define your target app directly
  const TARGET_APP = "com.instagram.android";
  
  // Launch the app
  ctx.app.launch(TARGET_APP);
  ctx.device.sleep(3000);
  
  // Your automation code...
  
  return { status: "ok" };
}
```

Simply define the package name as a constant at the top of your script and use it throughout.

## Finding UI Elements

To write effective automation scripts, you need to identify UI elements like buttons, text fields, and other controls. This section covers the best tools and methods for finding element identifiers such as resource IDs, text content, content descriptions, and class names.

### Why You Need UI Inspectors

DroidWright uses selectors to identify and interact with UI elements. To create reliable selectors, you need to know:
- **Resource ID**: Unique identifier for UI elements (most reliable)
- **Text**: Visible text on buttons, labels, etc.
- **Content Description**: Accessibility description (often used for icons)
- **Class Name**: Android view class type
- **Other Properties**: Clickable state, package name, etc.

### Recommended Tool: Appium Inspector

**Appium Inspector** is the most recommended tool for finding UI elements. It's specifically designed for mobile app automation and works perfectly with DroidWright's selector format.

#### Features
- Visual element selection on live device screenshots
- Real-time UI hierarchy view
- Detailed element attributes (resource ID, text, content description, etc.)
- XPath and UiAutomator selector generation
- Compatible with DroidWright's selector format

#### Installation

1. **Install Appium Server**:
   ```bash
   npm install -g appium
   npm install -g @appium/doctor
   ```

2. **Install Appium Inspector**:
   - Download from [Appium Inspector Releases](https://github.com/appium/appium-inspector/releases)
   - Or install via: `npm install -g appium-inspector`

3. **Setup Requirements**:
   - Android SDK with ADB
   - USB debugging enabled on your device
   - Appium server running

#### Usage

1. **Start Appium Server**:
   ```bash
   appium
   ```

2. **Connect Your Device**:
   ```bash
   adb devices
   ```

3. **Launch Appium Inspector**:
   - Open Appium Inspector
   - Configure connection settings:
     - **Platform Name**: `Android`
     - **Device Name**: Your device name
     - **App Package**: Package name of target app (e.g., `com.instagram.android`)
     - **App Activity**: Main activity (optional)
   - Click "Start Session"

4. **Inspecting Elements**:
   - Click on any element in the screenshot
   - View element details in the right panel
   - Copy resource ID, text, content description, etc.
   - Use the generated selectors in your DroidWright scripts

#### Example

![Appium Inspector](assets/appium-inspactor.png)

In the screenshot above, you can see:
- **Left Panel**: Live screenshot of the app with selectable elements
- **Middle Panel**: UI hierarchy (source code view)
- **Right Panel**: Selected element details with all attributes

The highlighted "Like" button shows:
- **Resource ID**: `com.instagram.android:id/row_feed_button_like`
- **Content Description**: `Like`
- **Class**: `android.widget.Button`

You can use this in your script:
```javascript
// Using resource ID (most reliable)
ctx.ui.tap({ id: "com.instagram.android:id/row_feed_button_like" });

// Using content description
ctx.ui.tap({ desc: "Like" });

// Combined selector (most reliable)
ctx.ui.tap({ 
  id: "com.instagram.android:id/row_feed_button_like",
  desc: "Like",
  clickable: "true"
});
```

### Alternative Tools

#### Developer Assistant (Android App)

**Developer Assistant** is a great alternative if you prefer an Android app that runs directly on your device without needing a computer.

**Installation**:
1. Download from [Google Play Store](https://play.google.com/store/apps/details?id=com.schibsted.developer)
2. Enable accessibility service
3. Launch the app you want to inspect
4. Use Developer Assistant to view UI elements

![Developer Assistant](assets/developer-assistant.png)


#### ADB and UI Automator Viewer

If you have ADB setup, you can use Android's built-in tools:

1. **UI Automator Viewer**:
   ```bash
   # Located in Android SDK
   <android-sdk>/tools/bin/uiautomatorviewer
   ```

2. **ADB Commands**:
   ```bash
   # Dump UI hierarchy
   adb shell uiautomator dump /sdcard/ui_dump.xml
   adb pull /sdcard/ui_dump.xml
   
   # Get element info
   adb shell dumpsys accessibility
   ```

#### Other Layout Inspector Apps

There are several other Android apps available on the Play Store that can help inspect UI layouts:

- **Layout Inspector** apps
- **UI Automator** tools
- **Accessibility Inspector** apps

Search for "layout inspector" or "UI inspector" in the Play Store to find tools that work for your needs.

### Best Practices for Finding Elements

1. **Use Resource IDs First**: Resource IDs are the most reliable selectors
   ```javascript
   // ✅ Best - Resource ID
   ctx.ui.tap({ id: "com.example.app:id/button_login" });
   ```

2. **Fallback to Text or Description**: If no resource ID, use text or content description
   ```javascript
   // ✅ Good - Content description
   ctx.ui.tap({ desc: "Login button" });
   
   // ✅ Good - Text
   ctx.ui.tap({ text: "Login" });
   ```

3. **Combine Multiple Properties**: More specific selectors are more reliable
   ```javascript
   // ✅ Best - Combined selector
   ctx.ui.tap({
     id: "button_login",
     text: "Login",
     clickable: "true",
     package: "com.example.app"
   });
   ```

4. **Test Your Selectors**: Always test selectors before using them in automation
   ```javascript
   // Test if element exists
   if (ctx.ui.exists({ id: "button_login" })) {
     ctx.ui.tap({ id: "button_login" });
   } else {
     log("Login button not found");
   }
   ```

5. **Handle Dynamic Content**: Some apps have dynamic IDs or text
   ```javascript
   // Use partial text match or other attributes
   ctx.ui.tap({ 
     className: "android.widget.Button",
     clickable: "true",
     package: "com.example.app"
   });
   ```

### Common Element Properties

When inspecting elements, look for these properties:

| Property | Description | Example |
|----------|-------------|---------|
| `resource-id` | Unique element identifier | `com.instagram.android:id/like_button` |
| `text` | Visible text content | `"Login"`, `"8,782"` |
| `content-desc` | Accessibility description | `"Like button"`, `"Search"` |
| `class` | Android view class | `android.widget.Button` |
| `package` | App package name | `com.instagram.android` |
| `clickable` | Whether element is clickable | `true`, `false` |
| `enabled` | Whether element is enabled | `true`, `false` |
| `bounds` | Element position and size | `[0,100][200,150]` |

### Converting Inspector Output to DroidWright Selectors

#### From Appium Inspector

Appium Inspector shows elements like this:
```
resource-id: com.instagram.android:id/row_feed_button_like
content-desc: Like
class: android.widget.Button
```

Convert to DroidWright selector:
```javascript
ctx.ui.tap({
  id: "com.instagram.android:id/row_feed_button_like",
  desc: "Like",
  className: "android.widget.Button"
});
```

#### From UiAutomator Selector

If you have a UiAutomator selector:
```
resourceId("com.instagram.android:id/row_feed_button_like")
```

You can use it directly as a string:
```javascript
ctx.ui.tap("resourceId(\"com.instagram.android:id/row_feed_button_like\")");
```

Or convert to object format:
```javascript
ctx.ui.tap({ id: "com.instagram.android:id/row_feed_button_like" });
```

### Troubleshooting Element Finding

**Problem**: Element not found even though it's visible

**Solutions**:
1. Check if element is in a different package/activity
2. Verify resource ID is correct (some apps use dynamic IDs)
3. Try using text or content description instead
4. Check if element is in a scrollable container
5. Use `ui.dumpTree()` to see available elements

```javascript
// Dump UI tree to see what's available
const tree = ctx.ui.dumpTree(3);
log(`Available elements: ${JSON.stringify(tree)}`);
```

**Problem**: Multiple elements match selector

**Solutions**:
1. Make selector more specific (add more properties)
2. Use `ui.findAll()` to get all matches and select the right one
3. Use index if elements are in a list

```javascript
// Get all buttons and select the second one
const buttons = ctx.ui.findAll({ className: "android.widget.Button" });
if (buttons.length > 1) {
  // Access specific element by index
  log(`Found ${buttons.length} buttons`);
}
```

## API Reference

### App API (`ctx.app`)

Control app launching and management.

#### `app.launch(packageName: string): boolean`

Launches an Android app by package name.

```javascript
// Launch Instagram
ctx.app.launch("com.instagram.android");
ctx.device.sleep(3000); // Wait for app to load
```

**Common Package Names**:
- Instagram: `com.instagram.android`
- WhatsApp: `com.whatsapp`
- Facebook: `com.facebook.katana`
- Twitter: `com.twitter.android`
- YouTube: `com.google.android.youtube`

#### `app.close(packageName: string): boolean`

Closes/kills an app by package name.

```javascript
ctx.app.close("com.instagram.android");
```

#### `app.getPackageName(): string | null`

Gets the package name of the currently active app.

```javascript
const currentApp = ctx.app.getPackageName();
log(`Current app: ${currentApp}`);
```

#### `app.openUri(uri: string): boolean`

Opens a URI or deep link using Android's `ACTION_VIEW` intent (e.g., Obsidian note URLs, web links, custom app schemes).

```javascript
// Open or create a note in Obsidian
ctx.app.openUri("obsidian://new?vault=MyVault&name=NewNote&content=Hello%20World");

// Open a web page in browser
ctx.app.openUri("https://example.com");

// Open a location in Maps
ctx.app.openUri("geo:37.7749,-122.4194");
```

#### `app.launchActivity(options: object): boolean`

Launches an Android Activity with custom Intent options (action, URI, package, MIME type, categories, and extras).

**Options**:
- `action` (optional, string): Android Intent action (e.g. `"android.intent.action.VIEW"`, `"android.intent.action.SEND"`).
- `uri` or `data` (optional, string): Data URI for the intent.
- `type` (optional, string): Explicit MIME type (e.g. `"text/plain"`).
- `package` (optional, string): Target package name.
- `component` (optional, string): Component identifier (e.g. `"com.example/.MainActivity"`).
- `categories` (optional, string[]): List of intent categories.
- `extras` (optional, object): Key-value pairs for Intent extras (supports strings, numbers, booleans).

```javascript
// Share text to an app
ctx.app.launchActivity({
  action: "android.intent.action.SEND",
  type: "text/plain",
  extras: {
    "android.intent.extra.TEXT": "Hello from DroidWright!"
  }
});

// Explicitly target Obsidian
ctx.app.launchActivity({
  action: "android.intent.action.VIEW",
  package: "md.obsidian",
  uri: "obsidian://new?vault=MyVault&name=Note"
});
```

### Device API (`ctx.device`)

Control device-level operations.

#### `device.sleep(ms: number): void`

Sleeps for the specified milliseconds. Minimum 100ms enforced.

```javascript
ctx.device.sleep(2000); // Sleep for 2 seconds
```

**Important**: Always add delays after UI actions to allow the app to respond.

#### `device.press(key: string): boolean`

Presses a system key.

```javascript
ctx.device.press("BACK");  // Back button
ctx.device.press("HOME");  // Home button
```

#### `device.getClipboard(): string`

Gets the current clipboard content.

```javascript
const clipboard = ctx.device.getClipboard();
log(`Clipboard: ${clipboard}`);
```

#### `device.setClipboard(text: string): void`

Sets the clipboard content.

```javascript
ctx.device.setClipboard("Hello, World!");
```

#### `device.getScreenSize(): {width: number, height: number}`

Gets the device screen dimensions.

```javascript
const size = ctx.device.getScreenSize();
log(`Screen: ${size.width}x${size.height}`);
```

#### `device.showToast(message: string): void`

Shows a toast message on screen.

```javascript
ctx.device.showToast("Script completed!");
```

### UI API (`ctx.ui`)

Interact with UI elements.

#### `ui.tap(selector: object): boolean`

Taps on an element matching the selector.

```javascript
// Tap by text
ctx.ui.tap({ text: "Login" });

// Tap by resource ID
ctx.ui.tap({ id: "login_button" });

// Tap by description
ctx.ui.tap({ desc: "Login button" });

// Combined selector
ctx.ui.tap({ 
  id: "button", 
  text: "Submit",
  clickable: "true"
});
```

#### `ui.longTap(selector: object): boolean`

Performs a long tap (press and hold).

```javascript
ctx.ui.longTap({ text: "Options" });
ctx.device.sleep(1500);
```

#### `ui.setText(selector: object, text: string): boolean`

Sets text in an input field.

```javascript
// Find input field and type text
ctx.ui.setText({ id: "username_input" }, "myusername");
ctx.device.sleep(500);
ctx.ui.setText({ id: "password_input" }, "mypassword");
```

#### `ui.scroll(selector: object, direction: string): boolean`

Scrolls a scrollable element.

```javascript
// Scroll down
ctx.ui.scroll({ id: "feed_list" }, "down");

// Scroll up
ctx.ui.scroll({ id: "feed_list" }, "up");
```

**Directions**: `"up"`, `"down"`, `"left"`, `"right"`, `"forward"`, `"backward"`

#### `ui.swipe(startX: number, startY: number, endX: number, endY: number, durationMs: number): boolean`

Performs a swipe gesture.

```javascript
// Swipe down (scroll up in feed)
const screenSize = ctx.device.getScreenSize();
const centerX = screenSize.width / 2;
const startY = screenSize.height * 0.8;  // 80% down
const endY = screenSize.height * 0.2;    // 20% down

ctx.ui.swipe(centerX, startY, centerX, endY, 500);
ctx.device.sleep(2000); // Wait for content to load
```

**Note**: Swipe operations have built-in rate limiting (minimum 3 seconds between swipes) to prevent ANR.

#### `ui.find(selector: object): object | null`

Finds a single element matching the selector.

```javascript
const element = ctx.ui.find({ text: "Login" });
if (element) {
  log(`Found element: ${JSON.stringify(element)}`);
} else {
  log("Element not found");
}
```

#### `ui.findAll(selector: object): array`

Finds all elements matching the selector.

```javascript
const buttons = ctx.ui.findAll({ className: "android.widget.Button" });
log(`Found ${buttons.length} buttons`);
```

#### `ui.exists(selector: object): boolean`

Checks if an element exists.

```javascript
if (ctx.ui.exists({ text: "Welcome" })) {
  log("Welcome screen is visible");
} else {
  log("Welcome screen not found");
}
```

#### `ui.waitFor(selector: object, timeoutMs: number, maxScrolls?: number, scrollContainer?: object): boolean`

Waits for an element to appear, optionally scrolling to find it.

```javascript
// Wait for element to appear (up to 5 seconds)
if (ctx.ui.waitFor({ text: "Feed" }, 5000)) {
  log("Feed loaded!");
} else {
  log("Feed did not appear");
}

// Wait with scrolling
ctx.ui.waitFor(
  { text: "Load More" },
  10000,           // 10 second timeout
  5,               // Max 5 scrolls
  { id: "feed_container" }  // Scroll container
);
```

#### `ui.waitForIdle(timeoutMs: number): boolean`

Waits for the UI to become idle (no animations).

```javascript
ctx.ui.waitForIdle(2000); // Wait up to 2 seconds
```

#### `ui.dumpTree(maxDepth?: number): array`

Dumps the accessibility tree for debugging.

```javascript
const tree = ctx.ui.dumpTree(3); // Max depth 3
log(`UI Tree: ${JSON.stringify(tree)}`);
```

### Network API (`ctx.network`)

Make HTTP requests.

#### `network.fetch(url: string, options?: object): string`

Makes an HTTP request.

```javascript
// GET request
const response = ctx.network.fetch("https://api.example.com/data");

// POST request
const response = ctx.network.fetch("https://api.example.com/endpoint", {
  method: "POST",
  headers: {
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ key: "value" })
});
```

**Options**:
- `method`: HTTP method (GET, POST, PUT, DELETE)
- `headers`: Object with header key-value pairs
- `body`: Request body as string

### Storage API (`ctx.storage`)

Persistent key-value storage.

#### `storage.put(key: string, value: string): void`

Stores a value.

```javascript
ctx.storage.put("lastRunTime", Date.now().toString());
ctx.storage.put("counter", "5");
```

#### `storage.get(key: string): string | null`

Retrieves a value.

```javascript
const lastRun = ctx.storage.get("lastRunTime");
if (lastRun) {
  log(`Last run: ${lastRun}`);
}
```

#### `storage.listKeys(): array`

Lists all stored keys.

```javascript
const keys = ctx.storage.listKeys();
log(`Stored keys: ${keys.join(", ")}`);
```

### Utility Functions

#### `log(message: string): void`

Logs a message to the console.

```javascript
log("Script started");
log(`Current time: ${Date.now()}`);
```

## Selectors

Selectors are objects that describe UI elements to find or interact with.

### Selector Properties

| Property | Description | Example |
|----------|-------------|---------|
| `id` / `resourceId` | Resource ID | `{ id: "login_button" }` |
| `text` | Exact text match | `{ text: "Login" }` |
| `desc` / `contentDesc` | Content description | `{ desc: "Login button" }` |
| `className` / `class` | Class name | `{ className: "android.widget.Button" }` |
| `package` / `packageName` | App package | `{ package: "com.instagram.android" }` |
| `clickable` | Clickable state | `{ clickable: "true" }` |
| `enabled` | Enabled state | `{ enabled: "true" }` |
| `checked` | Checked state | `{ checked: "true" }` |
| `selected` | Selected state | `{ selected: "true" }` |
| `focused` | Focused state | `{ focused: "true" }` |
| `visible` | Visible state | `{ visible: "true" }` |

### String Selectors (UiAutomator/XPath)

You can also use string selectors:

```javascript
// UiAutomator selector
ctx.ui.tap("resourceId(\"login_button\")");
ctx.ui.tap("text(\"Login\")");
ctx.ui.tap("description(\"Login button\")");

// Combined
ctx.ui.tap("resourceId(\"button\").text(\"Submit\")");
```

### Finding Elements

**Best Practice**: Use multiple selector properties for reliability:

```javascript
// More reliable - combines multiple properties
ctx.ui.tap({
  id: "like_button",
  clickable: "true",
  package: "com.instagram.android"
});
```

## Best Practices

### 1. Always Add Delays

```javascript
// ❌ Bad - no delay
ctx.ui.tap({ text: "Next" });

// ✅ Good - proper delay
ctx.ui.tap({ text: "Next" });
ctx.device.sleep(1000);
```

### 2. Wait for Elements

```javascript
// ✅ Wait for element before interacting
if (ctx.ui.waitFor({ text: "Feed" }, 5000)) {
  ctx.ui.tap({ text: "Post" });
} else {
  log("Feed not found");
  return { status: "error", note: "Feed not loaded" };
}
```

### 3. Handle Errors

```javascript
function droidRun(ctx) {
  try {
    ctx.app.launch("com.instagram.android");
    ctx.device.sleep(3000);
    
    if (!ctx.ui.exists({ text: "Home" })) {
      return { status: "error", note: "App did not load correctly" };
    }
    
    // Continue automation...
    
    return { status: "ok" };
  } catch (error) {
    log(`Error: ${error.message}`);
    return { status: "error", note: error.message };
  }
}
```

### 4. Use Screen Size for Swipes

```javascript
// ✅ Responsive to screen size
const size = ctx.device.getScreenSize();
const centerX = size.width / 2;
ctx.ui.swipe(centerX, size.height * 0.8, centerX, size.height * 0.2, 500);
```

### 5. Rate Limiting

The framework automatically rate-limits swipes (minimum 3 seconds between swipes). For other actions, add appropriate delays:

```javascript
// ✅ Good - proper spacing
for (let i = 0; i < 10; i++) {
  ctx.ui.swipe(540, 1800, 540, 600, 500);
  ctx.device.sleep(3000); // Wait between swipes
}
```

### 6. Logging

Use logging to debug and track progress:

```javascript
log("Starting automation");
log(`Screen size: ${JSON.stringify(ctx.device.getScreenSize())}`);
log(`Current app: ${ctx.app.getPackageName()}`);
```

### 7. Storage for State

Use storage to persist data between runs:

```javascript
// First run
const counter = parseInt(ctx.storage.get("counter") || "0");
ctx.storage.put("counter", (counter + 1).toString());

// Next run
const newCounter = ctx.storage.get("counter");
log(`Run count: ${newCounter}`);
```

## Examples

All example scripts are available in the [`examples/`](examples/) directory. You can import these directly into DroidWright or use them as templates for your own scripts.

### Available Examples

1. **[01_instagram_like_posts.js](examples/01_instagram_like_posts.js)**
   - Automates liking posts on Instagram
   - Demonstrates scrolling, element detection, and loops

2. **[02_form_filling.js](examples/02_form_filling.js)**
   - Shows how to fill out forms with text input
   - Demonstrates text field interaction

3. **[03_scroll_and_find.js](examples/03_scroll_and_find.js)**
   - Scrolls through a list to find a specific item
   - Uses `waitFor()` with scrolling

4. **[04_using_storage.js](examples/04_using_storage.js)**
   - Demonstrates persistent storage between script runs
   - Tracks state and timestamps

5. **[05_error_handling.js](examples/05_error_handling.js)**
   - Comprehensive error handling example
   - Validates app launch and UI state

6. **[06_ai_powered_automation.js](examples/06_ai_powered_automation.js)**
   - Integrates Google Gemini AI for intelligent automation
   - Requires API key setup

7. **[07_dynamic_selectors.js](examples/07_dynamic_selectors.js)**
   - Builds selectors dynamically
   - Reusable helper functions

8. **[08_looping_with_conditions.js](examples/08_looping_with_conditions.js)**
   - Retry logic with conditions
   - Scroll and search pattern

### Quick Example Preview

Here's a simple example to get you started:

```javascript
function droidRun(ctx) {
  log("Starting automation");
  
  // Launch app
  ctx.app.launch("com.instagram.android");
  ctx.device.sleep(3000);
  
  // Wait for UI
  if (!ctx.ui.waitFor({ text: "Home" }, 5000)) {
    return { status: "error", note: "App not loaded" };
  }
  
  // Perform action
  ctx.ui.tap({ text: "Search" });
  ctx.device.sleep(1000);
  
  return { status: "ok" };
}
```

For more complete examples, see the files in the [`examples/`](examples/) directory.

## Troubleshooting

### Element Not Found

**Problem**: `ui.tap()` returns `false` or element not found.

**Solutions**:
1. Use `ui.waitFor()` before interacting
2. Try different selector properties
3. Check if element is in a scrollable container
4. Use `ui.dumpTree()` to see available elements
5. Add delays to allow UI to load

```javascript
// Wait for element
if (ctx.ui.waitFor({ text: "Button" }, 5000)) {
  ctx.ui.tap({ text: "Button" });
} else {
  log("Button not found - checking UI tree");
  const tree = ctx.ui.dumpTree(2);
  log(JSON.stringify(tree));
}
```

### App Not Launching

**Problem**: `app.launch()` returns `false`.

**Solutions**:
1. Verify package name is correct
2. Check if app is installed
3. Add delay after launch
4. Check current app after launch

```javascript
const pkg = "com.instagram.android";
ctx.app.launch(pkg);
ctx.device.sleep(3000);

const current = ctx.app.getPackageName();
if (current === pkg) {
  log("App launched successfully");
} else {
  log(`App not launched. Current: ${current}`);
}
```

### Script Runs Too Fast

**Problem**: Actions happen before UI responds.

**Solution**: Add appropriate delays:

```javascript
// ✅ Good
ctx.ui.tap({ text: "Next" });
ctx.device.sleep(1000);
ctx.ui.waitForIdle(1000);
```

### Swipe Not Working

**Problem**: Swipes don't scroll or are too fast.

**Solutions**:
1. Use proper coordinates (relative to screen size)
2. Add delays after swipes
3. Check rate limiting (minimum 3 seconds between swipes)

```javascript
const size = ctx.device.getScreenSize();
ctx.ui.swipe(
  size.width / 2,      // Center X
  size.height * 0.8,   // Start Y (80% down)
  size.width / 2,      // End X (same)
  size.height * 0.2,   // End Y (20% down)
  500                  // Duration
);
ctx.device.sleep(3000); // Important: wait after swipe
```

### Import Issues

**Problem**: Script doesn't import correctly.

**Solutions**:
1. Ensure file is valid JavaScript
2. Check that `droidRun(ctx)` function exists
3. Verify file encoding (UTF-8)
4. Check file extension (.js)

## Advanced Topics

### AI-Powered Automation

You can integrate Google Gemini AI for intelligent automation. The AI agent can generate action sequences based on natural language tasks.

**See**: [examples/06_ai_powered_automation.js](examples/06_ai_powered_automation.js) for a complete implementation.

**Key Points**:
- Requires a Google Gemini API key
- AI generates JSON action arrays
- Actions are executed sequentially
- Includes error handling and retry logic

**Setup**:
1. Get a Gemini API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Add the key to your script
3. Define tasks in natural language
4. Let AI generate the automation steps

### Dynamic Selectors

Build selectors dynamically for more flexible automation:

**See**: [examples/07_dynamic_selectors.js](examples/07_dynamic_selectors.js) for complete examples.

**Benefits**:
- Reusable selector functions
- Conditional selector building
- Easier maintenance
- More readable code

### Looping with Conditions

Implement retry logic and conditional loops:

**See**: [examples/08_looping_with_conditions.js](examples/08_looping_with_conditions.js) for a complete example.

**Patterns**:
- Retry with max attempts
- Scroll and search
- Wait for conditions
- Break on success

### Performance Optimization

#### Minimize Delays
Only use delays when necessary:

```javascript
// ❌ Too many delays
ctx.ui.tap({ text: "Button" });
ctx.device.sleep(1000);
ctx.ui.waitForIdle(1000);
ctx.device.sleep(500);

// ✅ Efficient
ctx.ui.tap({ text: "Button" });
ctx.device.sleep(1000); // Single delay is usually enough
```

#### Batch Operations
Group related operations:

```javascript
// ✅ Good - batch form filling
ctx.ui.setText({ id: "field1" }, "value1");
ctx.ui.setText({ id: "field2" }, "value2");
ctx.ui.setText({ id: "field3" }, "value3");
ctx.device.sleep(1000); // Single delay after all inputs
```

#### Use waitForIdle Sparingly
Only when animations are critical:

```javascript
// ✅ When needed
ctx.ui.tap({ text: "Animated Button" });
ctx.ui.waitForIdle(2000); // Wait for animation

// ❌ Unnecessary
ctx.ui.tap({ text: "Static Button" });
ctx.ui.waitForIdle(2000); // Not needed
```

### Debugging Techniques

#### 1. Use Logging Extensively

```javascript
function droidRun(ctx) {
  log("=== Script Started ===");
  log(`Screen size: ${JSON.stringify(ctx.device.getScreenSize())}`);
  log(`Current app: ${ctx.app.getPackageName()}`);
  
  // Log each step
  log("Step 1: Launching app");
  ctx.app.launch("com.example.app");
  
  log("Step 2: Waiting for UI");
  ctx.device.sleep(2000);
  
  // Log results
  const found = ctx.ui.exists({ text: "Target" });
  log(`Target found: ${found}`);
}
```

#### 2. Dump UI Tree

```javascript
// Get full UI structure
const tree = ctx.ui.dumpTree(3);
log(`UI Tree: ${JSON.stringify(tree, null, 2)}`);
```

#### 3. Test Selectors

```javascript
// Test if selector works
const element = ctx.ui.find({ text: "Button" });
if (element) {
  log(`Found: ${JSON.stringify(element)}`);
  ctx.ui.tap({ text: "Button" });
} else {
  log("Button not found - trying alternatives");
  // Try other selectors
}
```

#### 4. Validate App State

```javascript
// Verify app launched correctly
const expectedApp = "com.example.app";
ctx.app.launch(expectedApp);
ctx.device.sleep(3000);

const currentApp = ctx.app.getPackageName();
if (currentApp !== expectedApp) {
  log(`ERROR: Expected ${expectedApp}, got ${currentApp}`);
  return { status: "error", note: "App launch failed" };
}
```

### Common Patterns

#### Pattern 1: Launch and Wait

```javascript
const APP_PKG = "com.example.app";
ctx.app.launch(APP_PKG);
ctx.device.sleep(3000);

if (!ctx.ui.waitFor({ text: "Main" }, 5000)) {
  return { status: "error", note: "App did not load" };
}
```

#### Pattern 2: Safe Tap

```javascript
function safeTap(ctx, selector) {
  if (ctx.ui.exists(selector)) {
    ctx.ui.tap(selector);
    ctx.device.sleep(1000);
    return true;
  }
  log(`Element not found: ${JSON.stringify(selector)}`);
  return false;
}

// Usage
if (!safeTap(ctx, { text: "Button" })) {
  return { status: "error", note: "Button not found" };
}
```

#### Pattern 3: Scroll Until Found

```javascript
function scrollUntilFound(ctx, selector, maxScrolls = 10) {
  for (let i = 0; i < maxScrolls; i++) {
    if (ctx.ui.exists(selector)) {
      return true;
    }
    
    const size = ctx.device.getScreenSize();
    ctx.ui.swipe(
      size.width / 2,
      size.height * 0.8,
      size.width / 2,
      size.height * 0.2,
      500
    );
    ctx.device.sleep(2000);
  }
  return false;
}
```

#### Pattern 4: Retry with Backoff

```javascript
function retryWithBackoff(ctx, action, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    if (action()) {
      return true;
    }
    const delay = 1000 * (i + 1); // Exponential backoff
    log(`Retry ${i + 1}/${maxRetries} after ${delay}ms`);
    ctx.device.sleep(delay);
  }
  return false;
}
```

### Security Considerations

1. **API Keys**: Never commit API keys to version control
2. **Sensitive Data**: Use storage API carefully for sensitive information
3. **Permissions**: Only request necessary permissions
4. **Rate Limiting**: Respect app rate limits and terms of service

### Best Practices Summary

1. ✅ Always add delays after UI actions
2. ✅ Use `waitFor()` before interacting with elements
3. ✅ Handle errors gracefully
4. ✅ Use screen size for responsive swipes
5. ✅ Log important steps for debugging
6. ✅ Validate app state before automation
7. ✅ Use storage for state persistence
8. ✅ Implement retry logic for unreliable operations
9. ✅ Test scripts on different screen sizes
10. ✅ Follow app terms of service

---

## Additional Resources

- **Example Scripts**: See the [`examples/`](examples/) directory for complete, runnable scripts
- **GitHub Repository**: [https://github.com/tas33n/droidwright](https://github.com/tas33n/droidwright)
- **Issues**: Report bugs or request features on GitHub Issues
- **Contributions**: Pull requests welcome!

**Need Help?** Check the [README.md](README.md) or open an issue on GitHub.
