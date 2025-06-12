package com.goodwy.smsmessenger.extensions

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}

// Trying to get the code from the SMS to offer to copy in the notification
fun String.getNumbersFromText(): String? {
    val numbers = Regex("(?=.*\\d)[\\d.,]+").findAll(this)
        .map(MatchResult::value)
        .toList()
    return numbers.firstOrNull { it.count() in 4..6 && "." !in it && "," !in it }
}

// Trying to get numbers, dates, amounts from SMS to offer to copy
fun String.getListNumbersFromText(): List<String> {
    val numbers = Regex("(?=.*\\d)[\\d.,]+").findAll(this)
        .map(MatchResult::value)
        .toList()
    return numbers.filter { it.count() in 4..30 }
}
