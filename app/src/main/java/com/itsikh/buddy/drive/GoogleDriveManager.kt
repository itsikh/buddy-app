package com.itsikh.buddy.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.gson.Gson
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.MemoryCategory
import com.itsikh.buddy.data.models.MemoryFact
import com.itsikh.buddy.data.models.SessionLog
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.models.VocabularyItem
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Drive AppData folder sync via the Drive REST API.
 *
 * Uses OkHttp directly (no Drive SDK) to avoid heavy transitive dependencies.
 * Authentication is handled by Google Sign-In — the access token is retrieved via
 * [GoogleAuthUtil.getToken], which automatically refreshes expired tokens.
 *
 * SETUP REQUIRED:
 * 1. Create a project in Google Cloud Console
 * 2. Enable "Google Drive API"
 * 3. Create OAuth 2.0 credentials (Android app type)
 * 4. Add your app's SHA-1 fingerprint + package name (com.itsikh.buddy)
 * 5. The parent must sign in via [getSignInIntent] for Drive access to work
 *
 * All files are stored in the app's private AppData folder — invisible to the user
 * in the Drive UI, protected from accidental deletion.
 */
@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val profileRepository: ProfileRepository,
    private val memoryRepository: MemoryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val conversationRepository: ConversationRepository
) {
    companion object {
        private const val TAG = "GoogleDriveManager"
        private val JSON = "application/json".toMediaType()
    }

    /** Returns the Google Sign-In options configured for Drive AppData access. */
    fun getSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(AppConfig.DRIVE_APPDATA_SCOPE))
            .build()

    fun getSignInClient() = GoogleSignIn.getClient(context, getSignInOptions())

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(): Boolean = getSignedInAccount()?.let {
        GoogleSignIn.hasPermissions(
            it,
            com.google.android.gms.common.api.Scope(AppConfig.DRIVE_APPDATA_SCOPE)
        )
    } == true

    /**
     * Syncs all child data to Drive AppData.
     * Call this after each session via [DriveSyncWorker].
     * Safe to call repeatedly — overwrites previous files.
     */
    suspend fun syncToDrive(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getAccessToken()
                ?: return@runCatching Unit.also {
                    AppLogger.w(TAG, "Drive sync skipped — not signed in")
                }

            val profile = profileRepository.getProfile()
                ?: return@runCatching Unit.also {
                    AppLogger.w(TAG, "Drive sync skipped — no profile")
                }

            var failures = 0
            runCatching { uploadProfile(token, profile) }
                .onFailure { AppLogger.e(TAG, "Upload profile failed: ${it.message}"); failures++ }
            runCatching { uploadVocabulary(token, profile.id) }
                .onFailure { AppLogger.e(TAG, "Upload vocabulary failed: ${it.message}"); failures++ }
            runCatching { uploadMemory(token, profile.id) }
                .onFailure { AppLogger.e(TAG, "Upload memory failed: ${it.message}"); failures++ }
            runCatching { uploadSessions(token, profile.id) }
                .onFailure { AppLogger.e(TAG, "Upload sessions failed: ${it.message}"); failures++ }
            runCatching { uploadDefaultPolicy(token) }
                .onFailure { AppLogger.e(TAG, "Upload policy failed: ${it.message}"); failures++ }

            if (failures == 0) {
                profileRepository.updateDriveStatus(profile.id, getSignedInAccount()?.email)
                AppLogger.i(TAG, "Drive sync complete")
            } else {
                AppLogger.w(TAG, "Drive sync completed with $failures upload failure(s)")
            }
        }
    }

    /**
     * Restores all child data from Drive AppData.
     * Overwrites local database — use only for first-install restore or explicit user request.
     */
    suspend fun restoreFromDrive(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getAccessToken()
                ?: throw IllegalStateException("Not signed in to Google Drive")

            val fileMap = listDriveFiles(token)

            if (DriveFileNames.PROFILE !in fileMap) {
                throw IllegalStateException("No backup found in Drive")
            }

            // Use existing local profile ID if available, otherwise create a new one.
            // All restored data is attached to this ID.
            val profileId = profileRepository.getProfile()?.id ?: UUID.randomUUID().toString()

            // ── Restore profile ──────────────────────────────────────────────
            fileMap[DriveFileNames.PROFILE]?.let { fileId ->
                val dp = gson.fromJson(downloadFile(token, fileId), DriveProfile::class.java)
                profileRepository.saveProfile(
                    ChildProfile(
                        id                    = profileId,
                        displayName           = dp.displayName,
                        age                   = dp.age,
                        gender                = dp.gender,
                        namePhonetic          = dp.namePhonetic,
                        cefrLevel             = dp.cefrLevel,
                        speakingLevel         = dp.speakingLevel,
                        vocabularyLevel       = dp.vocabularyLevel,
                        grammarLevel          = dp.grammarLevel,
                        totalSessionMinutes   = dp.totalSessionMinutes,
                        createdAt             = dp.createdAt,
                        lastSessionAt         = dp.lastSessionAt,
                        streakDays            = dp.streakDays,
                        lastStreakDate        = dp.lastStreakDate,
                        longestStreak         = dp.longestStreak,
                        streakShieldsAvailable = dp.streakShieldsAvailable,
                        xpTotal               = dp.xpTotal,
                        vocabularyMastered    = dp.vocabularyMastered,
                        coins                 = dp.coins,
                        onboardingComplete    = dp.onboardingComplete,
                        parentConsentGiven    = dp.parentConsentGiven
                    )
                )
                AppLogger.i(TAG, "Restore: profile ${dp.displayName}")
            }

            // ── Restore vocabulary ───────────────────────────────────────────
            fileMap[DriveFileNames.VOCABULARY]?.let { fileId ->
                val store = gson.fromJson(downloadFile(token, fileId), DriveVocabularyStore::class.java)
                vocabularyRepository.deleteAllForProfile(profileId)
                val items = store.items.map { v ->
                    VocabularyItem(
                        profileId        = profileId,
                        word             = v.word,
                        definition       = v.definition,
                        firstSeen        = v.firstSeen,
                        lastReviewed     = v.lastReviewed,
                        nextReviewDue    = v.nextReviewDue,
                        easeFactor       = v.easeFactor,
                        successfulRecalls = v.successfulRecalls,
                        failedRecalls    = v.failedRecalls,
                        masteryLevel     = v.masteryLevel
                    )
                }
                vocabularyRepository.insertAll(items)
                AppLogger.i(TAG, "Restore: ${items.size} vocabulary items")
            }

            // ── Restore memory ───────────────────────────────────────────────
            fileMap[DriveFileNames.MEMORY]?.let { fileId ->
                val store = gson.fromJson(downloadFile(token, fileId), DriveMemoryStore::class.java)
                memoryRepository.deleteAllForProfile(profileId)
                val facts = store.facts.mapNotNull { f ->
                    runCatching {
                        MemoryFact(
                            profileId = profileId,
                            category  = MemoryCategory.valueOf(f.category),
                            key       = f.key,
                            value     = f.value,
                            updatedAt = f.updatedAt
                        )
                    }.getOrNull()
                }
                memoryRepository.saveAll(facts)
                AppLogger.i(TAG, "Restore: ${facts.size} memory facts")
            }

            // ── Restore sessions ─────────────────────────────────────────────
            fileMap[DriveFileNames.SESSIONS]?.let { fileId ->
                val store = gson.fromJson(downloadFile(token, fileId), DriveSessionStore::class.java)
                conversationRepository.deleteAllForProfile(profileId)
                val sessions = store.sessions.mapNotNull { s ->
                    runCatching {
                        SessionLog(
                            id                 = s.id,
                            profileId          = profileId,
                            mode               = ChatMode.valueOf(s.mode),
                            startedAt          = s.startedAt,
                            durationMinutes    = s.durationMinutes,
                            turnCount          = s.turnCount,
                            newWordsIntroduced = s.newWordsIntroduced,
                            sessionSummary     = s.sessionSummary
                        )
                    }.getOrNull()
                }
                conversationRepository.insertSessionLogs(sessions)
                AppLogger.i(TAG, "Restore: ${sessions.size} sessions")
            }

            profileRepository.updateDriveStatus(profileId, getSignedInAccount()?.email)
            AppLogger.i(TAG, "Drive restore complete")
        }
    }

    // ---- Private upload helpers ----

    private suspend fun uploadProfile(token: String, profile: ChildProfile) {
        val driveProfile = DriveProfile(
            displayName             = profile.displayName,
            age                     = profile.age,
            gender                  = profile.gender,
            namePhonetic            = profile.namePhonetic,
            cefrLevel               = profile.cefrLevel,
            speakingLevel           = profile.speakingLevel,
            vocabularyLevel         = profile.vocabularyLevel,
            grammarLevel            = profile.grammarLevel,
            totalSessionMinutes     = profile.totalSessionMinutes,
            createdAt               = profile.createdAt,
            lastSessionAt           = profile.lastSessionAt,
            streakDays              = profile.streakDays,
            lastStreakDate          = profile.lastStreakDate,
            longestStreak           = profile.longestStreak,
            streakShieldsAvailable  = profile.streakShieldsAvailable,
            xpTotal                 = profile.xpTotal,
            vocabularyMastered      = profile.vocabularyMastered,
            coins                   = profile.coins,
            onboardingComplete      = profile.onboardingComplete,
            parentConsentGiven      = profile.parentConsentGiven
        )
        uploadFile(token, DriveFileNames.PROFILE, gson.toJson(driveProfile))
    }

    private suspend fun uploadVocabulary(token: String, profileId: String) {
        val items = vocabularyRepository.getAll(profileId).map { item ->
            DriveVocabularyItem(
                word             = item.word,
                definition       = item.definition,
                firstSeen        = item.firstSeen,
                lastReviewed     = item.lastReviewed,
                nextReviewDue    = item.nextReviewDue,
                easeFactor       = item.easeFactor,
                successfulRecalls = item.successfulRecalls,
                failedRecalls    = item.failedRecalls,
                masteryLevel     = item.masteryLevel
            )
        }
        val store = DriveVocabularyStore(items = items)
        uploadFile(token, DriveFileNames.VOCABULARY, gson.toJson(store))
    }

    private suspend fun uploadMemory(token: String, profileId: String) {
        val facts = memoryRepository.getAll(profileId).map { fact ->
            DriveMemoryFact(
                category  = fact.category.name,
                key       = fact.key,
                value     = fact.value,
                updatedAt = fact.updatedAt
            )
        }
        val store = DriveMemoryStore(facts = facts)
        uploadFile(token, DriveFileNames.MEMORY, gson.toJson(store))
    }

    private suspend fun uploadSessions(token: String, profileId: String) {
        val sessions = conversationRepository.getRecentSessions(profileId, limit = 30).map { log ->
            DriveSessionEntry(
                id                 = log.id,
                mode               = log.mode.name,
                startedAt          = log.startedAt,
                durationMinutes    = log.durationMinutes,
                turnCount          = log.turnCount,
                newWordsIntroduced = log.newWordsIntroduced,
                sessionSummary     = log.sessionSummary
            )
        }
        val store = DriveSessionStore(sessions = sessions)
        uploadFile(token, DriveFileNames.SESSIONS, gson.toJson(store))
    }

    private suspend fun uploadDefaultPolicy(token: String) {
        // Only upload if the policy file doesn't exist yet — don't overwrite parent's settings
        val existing = listDriveFiles(token)
        if (DriveFileNames.POLICY !in existing) {
            uploadFile(token, DriveFileNames.POLICY, gson.toJson(DriveParentPolicy()))
        }
    }

    // ---- Drive REST API helpers ----

    /**
     * Lists all files in the AppData folder, returning a map of filename → file ID.
     */
    private fun listDriveFiles(token: String): Map<String, String> {
        val request = Request.Builder()
            .url("${AppConfig.DRIVE_API_BASE_URL}/files?spaces=appDataFolder&fields=files(id,name)")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive list error ${response.code}")
            }
            val body = response.body?.string() ?: return emptyMap()

            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(body, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val files = parsed["files"] as? List<Map<String, String>> ?: return emptyMap()

            return files.associate { it["name"]!! to it["id"]!! }
        }
    }

    /**
     * Uploads (or updates) a file in the AppData folder.
     * If a file with the same name already exists, it is updated (PATCH).
     * Otherwise a new file is created (POST multipart).
     */
    private fun uploadFile(token: String, fileName: String, content: String) {
        val existingFiles = listDriveFiles(token)
        val existingId    = existingFiles[fileName]

        if (existingId != null) {
            // Update existing file
            val request = Request.Builder()
                .url("${AppConfig.DRIVE_UPLOAD_URL}/files/$existingId?uploadType=media")
                .patch(content.toRequestBody(JSON))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive update error ${response.code} for $fileName")
                }
            }
        } else {
            // Create new file in AppData folder
            val metadataJson = gson.toJson(
                mapOf("name" to fileName, "parents" to listOf("appDataFolder"))
            )
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.MIXED)
                .addPart(metadataJson.toRequestBody("application/json".toMediaType()))
                .addPart(content.toRequestBody(JSON))
                .build()

            val request = Request.Builder()
                .url("${AppConfig.DRIVE_UPLOAD_URL}/files?uploadType=multipart&spaces=appDataFolder")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive create error ${response.code} for $fileName")
                }
            }
        }
        AppLogger.d(TAG, "Uploaded $fileName")
    }

    private fun downloadFile(token: String, fileId: String): String {
        val request = Request.Builder()
            .url("${AppConfig.DRIVE_API_BASE_URL}/files/$fileId?alt=media")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive download error ${response.code}")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty drive download")
        }
    }

    /**
     * Gets a fresh OAuth access token for the Drive API.
     * [GoogleAuthUtil.getToken] caches and auto-refreshes tokens — safe to call on every sync.
     * Must run on a background thread (IO dispatcher).
     *
     * Handles [com.google.android.gms.auth.UserRecoverableAuthException] by clearing the cached
     * token and retrying once — this fixes stale tokens after password changes or revoked access.
     */
    private fun getAccessToken(): String? {
        val account = getSignedInAccount() ?: return null
        val email = account.email ?: run {
            AppLogger.w(TAG, "Signed-in account has no email — cannot get token")
            return null
        }
        val androidAccount = Account(email, "com.google")
        val scope = "oauth2:${AppConfig.DRIVE_APPDATA_SCOPE}"
        return try {
            GoogleAuthUtil.getToken(context, androidAccount, scope)
        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
            // Token is stale or consent was revoked — clear cache and retry once
            AppLogger.w(TAG, "UserRecoverableAuthException — clearing token cache and retrying")
            try {
                GoogleAuthUtil.clearToken(context, e.intent?.getStringExtra("authtoken") ?: "")
                GoogleAuthUtil.getToken(context, androidAccount, scope)
            } catch (retryEx: Exception) {
                AppLogger.e(TAG, "Token retry also failed: ${retryEx.message}")
                null
            }
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            AppLogger.e(TAG, "GoogleAuthException (bad account or scope): ${e.message}")
            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get access token: ${e.message}")
            null
        }
    }
}
