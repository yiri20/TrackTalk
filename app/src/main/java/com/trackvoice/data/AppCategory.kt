package com.trackvoice.data

import java.util.Locale

enum class AppCategory(
    val title: String,
    val description: String,
) {
    MUSIC_STREAMING("음악 스트리밍", "음악을 중심으로 재생하는 앱"),
    MUSIC_VIDEO("음악·동영상", "영상과 뮤직비디오를 재생하는 앱"),
    LEARNING("학습·오디오북", "강의, 학습, 오디오북을 듣는 앱"),
    PODCAST("팟캐스트·라디오", "팟캐스트와 라디오를 듣는 앱"),
    OTHER("기타 미디어", "자동 분류되지 않은 미디어 앱"),
}

/**
 * Only music-streaming apps are enabled on first discovery. Other categories,
 * including unclassified apps, stay off until the user explicitly enables
 * them in the Apps screen.
 */
fun AppCategory.isGuideEnabledByDefault(): Boolean = this == AppCategory.MUSIC_STREAMING

/**
 * Shared app enablement policy. A nullable override is kept separate from the
 * effective value so category corrections can update apps that were never
 * explicitly changed by the user.
 */
object AppGuideEnablementPolicy {
    fun defaultEnabled(packageName: String, appName: String = ""): Boolean =
        categorizeApp(packageName, appName).isGuideEnabledByDefault()

    fun effectiveEnabled(
        packageName: String,
        appName: String,
        explicitOverride: Boolean?,
    ): Boolean = explicitOverride ?: defaultEnabled(packageName, appName)
}

fun categorizeApp(packageName: String, appName: String): AppCategory {
    val searchableText = "$packageName $appName".lowercase(Locale.ROOT)
    val explicitVideoApp = searchableText.containsAny(
        "music video",
        "video player",
        "뮤직비디오",
        "비디오 플레이어",
    )
    return when {
        explicitVideoApp -> AppCategory.MUSIC_VIDEO

        searchableText.containsAny(
            "spotify",
            "youtube.music",
            "music.youtube",
            "youtube music",
            "apple.android.music",
            "apple music",
            "amazon.mp3",
            "amazon music",
            "melon",
            "melOn".lowercase(Locale.ROOT),
            "genie",
            "bugs",
            "vibe",
            "flo",
            "tidal",
            "deezer",
            "soundcloud",
            "pandora",
            "qobuz",
            "joox",
            "audiomack",
            "boomplay",
            "멜론",
            "지니뮤직",
            "벅스",
            "바이브",
            "유튜브 뮤직",
            "스포티파이",
            "애플 뮤직",
            "사운드클라우드",
            "타이달",
            "뮤직",
        ) -> AppCategory.MUSIC_STREAMING

        searchableText.containsAny(
            "podcast",
            "castbox",
            "pocket casts",
            "pocketcasts",
            "overcast",
            "tunein",
            "radio",
            "팟캐스트",
            "팟빵",
            "라디오",
            "오디오클립",
        ) -> AppCategory.PODCAST

        searchableText.containsAny(
            "audible",
            "audiobook",
            "libby",
            "ridibooks",
            "millie",
            "udemy",
            "coursera",
            "khan",
            "duolingo",
            "learning",
            "study",
            "education",
            "school",
            "lecture",
            "class",
            "ebs",
            "오디오북",
            "전자책",
            "학습",
            "공부",
            "강의",
            "클래스",
            "교육",
            "밀리의 서재",
            "리디북스",
        ) -> AppCategory.LEARNING

        searchableText.containsAny(
            "com.google.android.youtube",
            "youtube",
            "tiktok",
            "musically",
            "vimeo",
            "netflix",
            "wavve",
            "watcha",
            "bilibili",
            "dailymotion",
            "twitch",
            "video",
            "동영상",
            "뮤직비디오",
            "틱톡",
            "유튜브",
            "넷플릭스",
            "웨이브",
            "왓챠",
        ) -> AppCategory.MUSIC_VIDEO

        else -> AppCategory.OTHER
    }
}

private fun String.containsAny(vararg candidates: String): Boolean = candidates.any { candidate -> contains(candidate) }
