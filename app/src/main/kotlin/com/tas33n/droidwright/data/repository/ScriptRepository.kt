/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.tas33n.droidwright.data.database.AppDatabase
import com.tas33n.droidwright.data.database.ScriptEntity
import com.tas33n.droidwright.data.models.AutomationScript
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object ScriptRepository {
    
    private var database: AppDatabase? = null
    private val gson = Gson()
    private const val PREFS_NAME = "script_repository_prefs"
    private const val KEY_DEFAULTS_VERSION = "defaults_version"
    private const val DEFAULTS_VERSION = 2
    private const val KEY_SCRIPTS_MIGRATED = "scripts_migrated_v2"
    
    fun initialize(context: Context) {
        if (database == null) {
            database = AppDatabase.getDatabase(context.applicationContext)
            // Initialize defaults and migrate existing scripts asynchronously
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                initializeDefaults(context.applicationContext)
                migrateExistingScripts(context.applicationContext)
            }
        }
    }
    
    private suspend fun initializeDefaults(context: Context) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentVersion = prefs.getInt(KEY_DEFAULTS_VERSION, 0)
            
            if (currentVersion >= DEFAULTS_VERSION) return@withContext
            
            val db = database ?: return@withContext
            val defaultScripts = createDefaultScripts()
            
            defaultScripts.forEach { script ->
                val entity = scriptToEntity(script)
                db.scriptDao().insertScript(entity)
            }
            
            prefs.edit().putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION).apply()
        }
    }
    
    /**
     * Migrate existing scripts to remove 'defaults' references
     */
    private suspend fun migrateExistingScripts(context: Context) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val migrated = prefs.getBoolean(KEY_SCRIPTS_MIGRATED, false)
            
            if (migrated) {
                return@withContext
            }
            
            val db = database ?: return@withContext
            val entities = db.scriptDao().getAllScripts().first()
            
            var updated = false
            entities.forEach { entity ->
                var code = entity.code
                val originalCode = code
                
                // Replace defaults.swipeStartRatio with 0.82
                code = code.replace(
                    Regex("""defaults\.swipeStartRatio\s*\|\|\s*0\.82"""),
                    "0.82"
                )
                code = code.replace(
                    Regex("""defaults\.swipeStartRatio"""),
                    "0.82"
                )
                
                // Replace defaults.swipeEndRatio with 0.28
                code = code.replace(
                    Regex("""defaults\.swipeEndRatio\s*\|\|\s*0\.28"""),
                    "0.28"
                )
                code = code.replace(
                    Regex("""defaults\.swipeEndRatio"""),
                    "0.28"
                )
                
                // If code was changed, update the entity
                if (code != originalCode) {
                    val updatedEntity = entity.copy(
                        code = code,
                        updatedAt = System.currentTimeMillis()
                    )
                    db.scriptDao().updateScript(updatedEntity)
                    updated = true
                }
            }
            
            // Mark migration as complete (even if no scripts were updated)
            prefs.edit().putBoolean(KEY_SCRIPTS_MIGRATED, true).apply()
        }
    }
    
    private fun scriptToEntity(script: AutomationScript): ScriptEntity {
        val tagsJson = gson.toJson(script.tags)
        return ScriptEntity.fromAutomationScript(script, tagsJson)
    }
    
    private fun entityToScript(entity: ScriptEntity): AutomationScript {
        val tags = gson.fromJson(entity.tagsJson, Array<String>::class.java)?.toList() ?: emptyList()
        var code = entity.code
        
        // Fix any remaining defaults references on-the-fly when loading
        if (code.contains("defaults.")) {
            code = code.replace(Regex("""defaults\.swipeStartRatio\s*\|\|\s*0\.82"""), "0.82")
            code = code.replace(Regex("""defaults\.swipeStartRatio"""), "0.82")
            code = code.replace(Regex("""defaults\.swipeEndRatio\s*\|\|\s*0\.28"""), "0.28")
            code = code.replace(Regex("""defaults\.swipeEndRatio"""), "0.28")
        }
        
        return entity.toAutomationScript(tags).copy(code = code)
    }


    fun scriptsFlow(): Flow<List<AutomationScript>> {
        val db = database ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.scriptDao().getAllScripts().map { entities ->
            entities.map { entityToScript(it) }
        }
    }

    suspend fun getAllScripts(): List<AutomationScript> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        val entities = db.scriptDao().getAllScripts().first()
        entities.map { entityToScript(it) }
    }

    suspend fun getScriptById(id: String): AutomationScript? = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext null
        val entity = db.scriptDao().getScriptById(id)
        entity?.let { entityToScript(it) }
    }

    suspend fun addScript(script: AutomationScript) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val entity = scriptToEntity(script)
        db.scriptDao().insertScript(entity)
    }

    suspend fun updateScript(script: AutomationScript) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val entity = scriptToEntity(script)
        db.scriptDao().updateScript(entity)
    }

    suspend fun deleteScript(id: String) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.scriptDao().deleteScriptById(id)
    }
    
    suspend fun updateScriptRunningState(id: String, isRunning: Boolean) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.scriptDao().updateRunningState(id, isRunning, System.currentTimeMillis())
    }
    
    suspend fun updateScriptLastExecuted(id: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.scriptDao().updateLastExecuted(id, timestamp)
    }

    fun nextScriptId(): String = UUID.randomUUID().toString()
    
    private fun createDefaultScripts(): List<AutomationScript> {
        return listOf(
            AutomationScript(
                id = "instagram-like-comment",
                name = "Instagram Like & Comment",
                description = "Likes posts and adds comments on Instagram feed with scroll retry logic.",
                code = instagramEngagementDefaultScript(),
                tags = listOf("instagram", "social", "automation")
            )
        )
    }
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun instagramEngagementDefaultScript(): String {
        return """
            // ==DroidScript==
            // @id              instagram-like-comment
            // @name            Instagram Like & Comment
            // @description     Likes posts and adds comments on Instagram feed with scroll retry logic.
            // @author          tas33n
            // @version         1.0.0
            // @targetApp       com.instagram.android
            // @url             https://github.com/tas33n/droidwright
            // @created         2024-11-08
            // ==/DroidScript==

            /**
             * Main automation function
             * @param {Object} ctx - Context object containing device, ui, and app controllers
             * @param {Object} ctx.device - Device control methods (sleep, press, getScreenSize, showToast)
             * @param {Object} ctx.ui - UI interaction methods (find, tap, setText, swipe, exists)
             * @param {Object} ctx.app - App control methods (launch, stop, isRunning)
             * @returns {Object} Result object with status and note
             */
            function droidRun(ctx) {
              // Configuration
              const TOTAL_POSTS = 20;
              const MAX_RETRIES = 3;
              const COMMENTS = ["Nice!", "Great post!", "Love it!", "Amazing!", "Awesome!"];
              const settleMs = 1200;
              const swipeDurationMs = 500;
              const pollMs = 400;
              const targetPackage = typeof targetApp === "string" ? targetApp : "com.instagram.android";

              // Launch Instagram
              ctx.app.launch(targetPackage);
              ctx.device.sleep(settleMs);

              // Get screen dimensions for swipe calculations
              const screen = ctx.device.getScreenSize();
              const swipe = {
                startX: Math.round(screen.width * 0.5),
                startY: Math.round(screen.height * 0.82),
                endX: Math.round(screen.width * 0.5),
                endY: Math.round(screen.height * 0.28),
                duration: swipeDurationMs
              };

              // UI Element Selectors
              const likeSelector = { id: "com.instagram.android:id/row_feed_button_like" };
              const commentBtnSelector = { id: "com.instagram.android:id/row_feed_button_comment" };
              const commentTextSelector = { id: "com.instagram.android:id/layout_comment_thread_edittext" };
              const postBtnSelector = { id: "com.instagram.android:id/layout_comment_thread_post_button_icon" };

              // Counters
              let likedCount = 0;
              let commentedCount = 0;

              // Process posts
              for (let i = 0; i < TOTAL_POSTS; i++) {
                const label = `${"$"}{i + 1}/${"$"}{TOTAL_POSTS}`;
                let retries = 0;
                let liked = false;
                let commented = false;

                while (retries < MAX_RETRIES && (!liked || !commented)) {
                  // Try to like the post
                  if (!liked) {
                    const likeButton = ctx.ui.find(likeSelector);

                    if (likeButton) {
                      const desc = likeButton.desc || "";
                      const isAlreadyLiked = desc.toLowerCase().includes("liked");

                      if (!isAlreadyLiked && ctx.ui.tap(likeSelector)) {
                        liked = true;
                        likedCount++;
                        ctx.device.sleep(500);
                      } else if (isAlreadyLiked) {
                        liked = true;
                        ctx.device.sleep(200);
                      }
                    }
                  }

                  // Try to comment on the post
                  if (!commented && ctx.ui.exists(commentBtnSelector)) {
                    if (ctx.ui.tap(commentBtnSelector)) {
                      ctx.device.sleep(settleMs);

                      if (ctx.ui.exists(commentTextSelector)) {
                        const comment = COMMENTS[Math.floor(Math.random() * COMMENTS.length)];
                        ctx.ui.setText(commentTextSelector, comment);
                        ctx.device.sleep(500);

                        if (ctx.ui.exists(postBtnSelector)) {
                          if (ctx.ui.tap(postBtnSelector)) {
                            commented = true;
                            commentedCount++;
                            ctx.device.sleep(settleMs);
                            ctx.device.press("BACK");
                            ctx.device.press("BACK");
                            ctx.device.sleep(500);
                          }
                        }
                      }
                    }
                  }

                  // Retry logic
                  if ((!liked || !commented) && retries < MAX_RETRIES - 1) {
                    retries++;
                    ctx.ui.swipe(swipe.startX, swipe.startY, swipe.endX, swipe.endY, swipe.duration);
                    ctx.device.sleep(settleMs);
                  } else {
                    break;
                  }
                }

                // Show progress
                ctx.device.showToast(`${"$"}{label}: ${"$"}{liked ? "Liked" : "Skip"} ${"$"}{commented ? "+ Comment" : ""}`);

                // Scroll to next post
                if (i < TOTAL_POSTS - 1) {
                  ctx.ui.swipe(swipe.startX, swipe.startY, swipe.endX, swipe.endY, swipe.duration);
                  ctx.device.sleep(settleMs);
                }
              }

              // Return results
              return {
                status: "ok",
                note: `Liked ${"$"}{likedCount}, Commented ${"$"}{commentedCount} of ${"$"}{TOTAL_POSTS} posts`
              };
            }
        """.trimIndent()
    }
}
