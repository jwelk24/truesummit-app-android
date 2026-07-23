package com.truesummit.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

object SummitColors {
    val Slate = Color(0xFF1C2333)
    val Slate2 = Color(0xFF252E42)
    val Ice = Color(0xFFF0F4FF)
    val Teal = Color(0xFF4ECDC4)
    val TealDeep = Color(0xFF3AB8B0)
    val Amber = Color(0xFFF7B731)
    val Rose = Color(0xFFFF6B6B)
    val Lavender = Color(0xFF9B8EC4)

    val accentCycle = listOf(Teal, Amber, Rose, Lavender)
    fun accent(index: Int) = accentCycle[index % accentCycle.size]

    fun atmosphere(budgetUsed: Double): Color {
        val t = budgetUsed.coerceIn(0.0, 1.0).toFloat()
        return if (t < 0.5f) lerp(Teal, Amber, t * 2f)
        else lerp(Amber, Rose, (t - 0.5f) * 2f)
    }
}

fun summitCategoryEmoji(name: String?): String {
    if (name.isNullOrEmpty()) return "💵"
    val lower = name.lowercase()
    val mapping = listOf(
        listOf("rent", "mortgage", "housing", "home") to "🏠",
        listOf("grocer") to "🛒",
        listOf("coffee", "cafe") to "☕",
        listOf("dining", "restaurant", "food", "takeout", "eat") to "🍜",
        listOf("travel", "vacation", "flight", "trip") to "✈️",
        listOf("transport", "transit", "gas", "fuel", "car", "auto", "parking") to "🚗",
        listOf("subscription", "streaming") to "📺",
        listOf("utilit", "electric", "power", "water") to "💡",
        listOf("internet", "wifi") to "🌐",
        listOf("phone", "mobile", "cell") to "📱",
        listOf("health", "medical", "doctor", "dental") to "🩺",
        listOf("fitness", "gym", "workout") to "🏋️",
        listOf("entertainment", "movie", "game", "fun") to "🎬",
        listOf("shopping", "clothes", "clothing", "apparel") to "🛍️",
        listOf("gift", "charity", "giving", "donation") to "🎁",
        listOf("kid", "child", "baby", "daycare") to "🧸",
        listOf("pet", "dog", "cat", "vet") to "🐾",
        listOf("insurance") to "🛡️",
        listOf("education", "tuition", "school", "book", "course") to "🎓",
        listOf("saving", "emergency") to "🏦",
        listOf("income", "paycheck", "salary") to "💰",
        listOf("debt", "loan", "credit") to "💳",
        listOf("personal", "beauty", "hair", "care") to "💇",
        listOf("maintenance", "repair", "improvement") to "🛠️",
        listOf("tax") to "🧾",
    )
    for ((keywords, emoji) in mapping) {
        if (keywords.any { lower.contains(it) }) return emoji
    }
    return "💵"
}
