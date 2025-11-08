# DroidWright Example Scripts

This directory contains example automation scripts that demonstrate various features and patterns in DroidWright.

## How to Use

1. **Import into DroidWright**: Use the "Import Script" button in the app to load any of these scripts
2. **Use as Templates**: Copy and modify these scripts for your own automation needs
3. **Learn from Examples**: Study the code to understand best practices

## Available Examples

### 01_instagram_like_posts.js
**Purpose**: Automates liking posts on Instagram

**Features**:
- App launching
- Element detection with fallbacks
- Scrolling through feed
- Loop-based automation

**Usage**: Import and run. Make sure Instagram is installed.

---

### 02_form_filling.js
**Purpose**: Demonstrates form filling with text input

**Features**:
- Text field interaction
- Multiple input fields
- Success validation

**Usage**: Modify package name and field IDs to match your target app.

---

### 03_scroll_and_find.js
**Purpose**: Scrolls through a list to find a specific item

**Features**:
- `waitFor()` with scrolling
- Timeout handling
- Container-based scrolling

**Usage**: Update the target item text and container selector.

---

### 04_using_storage.js
**Purpose**: Demonstrates persistent storage between script runs

**Features**:
- Storing timestamps
- Retrieving stored values
- State persistence

**Usage**: Run multiple times to see storage persistence.

---

### 05_error_handling.js
**Purpose**: Comprehensive error handling example

**Features**:
- App launch validation
- UI state verification
- Try-catch error handling
- Detailed error messages

**Usage**: Study this to learn proper error handling patterns.

---

### 06_ai_powered_automation.js
**Purpose**: Integrates Google Gemini AI for intelligent automation

**Features**:
- AI agent integration
- Natural language task definition
- Action generation
- Error handling

**Requirements**:
- Google Gemini API key
- Internet connection

**Usage**:
1. Get API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Replace `YOUR_API_KEY_HERE` with your actual key
3. Modify the task description
4. Run the script

---

### 07_dynamic_selectors.js
**Purpose**: Builds selectors dynamically and uses helper functions

**Features**:
- Reusable selector functions
- Dynamic selector building
- Helper functions for common tasks

**Usage**: Use as a template for building reusable automation functions.

---

### 08_looping_with_conditions.js
**Purpose**: Retry logic with conditions and scrolling

**Features**:
- Retry loops
- Conditional breaking
- Scroll and search pattern
- Attempt tracking

**Usage**: Adapt for any "scroll until found" scenario.

## Tips

- **Modify Package Names**: Replace example package names with your target apps
- **Adjust Selectors**: Update selectors to match your target app's UI
- **Test Incrementally**: Test each part of the script before running the full automation
- **Check Logs**: Use the log output to debug and understand script execution
- **Respect Rate Limits**: Add appropriate delays to avoid overwhelming the target app

## Contributing

Have a useful example? Submit a pull request with your script following the naming convention: `##_descriptive_name.js`

---

For detailed documentation, see [../docs.md](../docs.md)

