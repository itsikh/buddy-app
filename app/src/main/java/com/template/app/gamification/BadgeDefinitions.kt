package com.template.app.gamification

/**
 * All badge definitions for the Buddy gamification system.
 *
 * Badges are designed to be specific and meaningful — celebrating genuine milestones
 * rather than arbitrary actions. Each badge has a name in Hebrew (shown in the UI)
 * and a condition description used by [BadgeEvaluator].
 */
data class Badge(
    val id: String,
    val nameHe: String,
    val descriptionHe: String,
    val icon: String,    // Emoji used as the badge icon
    val isSecret: Boolean = false  // Rare/surprise badges not shown until earned
)

object BadgeDefinitions {

    val ALL: List<Badge> = listOf(
        // ---- Streak badges ----
        Badge("streak_3",    "3 ימים ברצף!",        "שוחחת עם Buddy 3 ימים רצופים",           "🔥"),
        Badge("streak_7",    "שבוע שלם!",            "שבוע שלם של שיחות עם Buddy",              "⭐"),
        Badge("streak_14",   "שבועיים!",             "14 יום ברצף — מדהים!",                   "🌟"),
        Badge("streak_30",   "חודש מלא!",            "30 יום ברצף — גיבור אנגלית!",             "🏆"),

        // ---- Vocabulary badges ----
        Badge("vocab_10",    "10 מילים!",            "למדת 10 מילים אנגליות",                   "🌱"),
        Badge("vocab_25",    "25 מילים!",            "25 מילים אנגליות בגן שלך",                "🌿"),
        Badge("vocab_50",    "50 מילים!",            "50 מילים — גן ענק!",                      "🌳"),
        Badge("vocab_100",   "100 מילים!",           "100 מילים! אוצר מילים עשיר",              "🌲"),

        // ---- Conversation badges ----
        Badge("first_chat",  "שיחה ראשונה!",         "ניהלת את השיחה הראשונה שלך עם Buddy",     "👋"),
        Badge("chat_5",      "5 שיחות!",             "5 שיחות עם Buddy",                        "💬"),
        Badge("chat_20",     "20 שיחות!",            "20 שיחות — Buddy זוכר הכל!",              "🗣️"),

        // ---- CEFR level badges ----
        Badge("level_a1",    "רמה A1!",              "הגעת לרמת אנגלית A1",                     "📗"),
        Badge("level_a2",    "רמה A2!",              "הגעת לרמת אנגלית A2 — כל הכבוד!",        "📘"),
        Badge("level_b1",    "רמה B1!",              "הגעת לרמת אנגלית B1 — מדהים!",           "📙"),

        // ---- Story & Role-play badges ----
        Badge("first_story", "סיפורן!",              "השתתפת בסיפור הראשון שלך עם Buddy",       "📖"),
        Badge("first_rp",    "שחקן!",                "שיחקת משחק תפקידים לראשונה",              "🎭"),

        // ---- Secret/rare badges (not shown until earned) ----
        Badge("talkative",   "רב שיח!",              "דיברת יותר מ-3 דקות ברצף באנגלית",        "🎤", isSecret = true),
        Badge("comeback",    "חזרת!",                "חזרת לאחר הפסקה של יותר מ-7 ימים",        "💪", isSecret = true),
        Badge("early_bird",  "ציפור מוקדמת!",        "שוחחת עם Buddy לפני 8 בבוקר",            "🐦", isSecret = true)
    )

    fun findById(id: String): Badge? = ALL.find { it.id == id }
}
