package com.example.util

import android.text.Html
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

data class SharedLocation(
    val originalText: String,
    val placeName: String = "",
    val googlePlaceName: String = "",
    val address: String = "",
    val googleMapsUrl: String = "",
    val resolvedUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isKoreanLocation: Boolean = false
)

data class ResolvedPlaceInfo(
    val resolvedUrl: String,
    val ogTitle: String? = null,
    val ogDescription: String? = null
)

object GoogleMapsParser {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isHangul(c: Char): Boolean {
        val codePoint = c.code
        return (codePoint in 0xAC00..0xD7A3) || // Hangul Syllables
               (codePoint in 0x1100..0x11FF) || // Hangul Jamo
               (codePoint in 0x3130..0x318F) || // Hangul Compatibility Jamo
               (codePoint in 0xA960..0xA97F) || // Hangul Jamo Extended-A
               (codePoint in 0xD7B0..0xD7FF)    // Hangul Jamo Extended-B
    }

    fun extractUrl(text: String): String? {
        val urlRegex = Regex("https?://[^\\s\\n]+")
        return urlRegex.find(text)?.value
    }

    suspend fun resolveShortUrl(urlString: String): ResolvedPlaceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure we request with Korean language headers to obtain native Korean names & addresses
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val html = response.body?.string() ?: ""
                    
                    val title = extractMetaTag(html, "og:title")?.let { cleanTitle(it) }
                    val description = extractMetaTag(html, "og:description")
                    
                    ResolvedPlaceInfo(
                        resolvedUrl = finalUrl,
                        ogTitle = title,
                        ogDescription = description
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun extractMetaTag(html: String, property: String): String? {
        try {
            val patterns = listOf(
                Regex("<meta\\s+property=\"$property\"\\s+content=\"([^\"]+)\""),
                Regex("<meta\\s+content=\"([^\"]+)\"\\s+property=\"$property\""),
                Regex("<meta\\s+name=\"$property\"\\s+content=\"([^\"]+)\""),
                Regex("<meta\\s+content=\"([^\"]+)\"\\s+name=\"$property\"")
            )
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val rawContent = match.groupValues[1]
                    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(rawContent, Html.FROM_HTML_MODE_LEGACY).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(rawContent).toString()
                    }
                    return decoded.trim()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun cleanTitle(title: String): String {
        var cleaned = title
        val suffixes = listOf(" - Google Maps", " - Google 地圖", " - Google 맵", " - Google", " – Google", "- Google")
        for (suffix in suffixes) {
            if (cleaned.contains(suffix)) {
                cleaned = cleaned.substringBefore(suffix).trim()
            }
        }
        return cleaned
    }

    fun extractAddressFromDescription(description: String): String? {
        if (description.isBlank()) return null
        
        // Split by middle dot or other common separators
        val parts = description.split(Regex("[·•|─,-]")).map { it.trim() }
        
        // Check if a segment resembles a Korean address
        val provinceKeywords = listOf(
            "특별", "광역시", "자치", "경기도", "강원", "충청", "전라", "경상", "제주",
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "충북", "충남", "전북", "전남", "경북", "경남"
        )
        
        for (part in parts) {
            val cleanPart = part.trim()
            if (cleanPart.isEmpty()) continue
            
            // Check if it contains Hangul
            var hasHangul = false
            for (char in cleanPart) {
                if (isHangul(char)) {
                    hasHangul = true
                    break
                }
            }
            
            if (hasHangul) {
                val hasProvince = provinceKeywords.any { cleanPart.contains(it) }
                val hasAddressSuffix = listOf("시 ", "구 ", "군 ", "동 ", "로 ", "길 ").any { cleanPart.contains(it) }
                
                if (hasProvince || hasAddressSuffix) {
                    return cleanPart
                }
            }
        }
        
        // Fallback: return the first part that has Hangul
        for (part in parts) {
            val cleanPart = part.trim()
            var hasHangul = false
            for (char in cleanPart) {
                if (isHangul(char)) {
                    hasHangul = true
                    break
                }
            }
            if (hasHangul && cleanPart.length > 5 && !cleanPart.startsWith("★")) {
                return cleanPart
            }
        }
        
        return null
    }

    fun decodePlaceNameFromUrl(url: String): String? {
        try {
            val placeRegex = Regex("/place/([^/@?]+)")
            val match = placeRegex.find(url) ?: Regex("place/([^/@?]+)").find(url)
            if (match != null) {
                val encodedName = match.groupValues[1]
                return URLDecoder.decode(encodedName.replace("+", " "), "UTF-8")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun extractCoordinates(url: String): Pair<Double, Double>? {
        // 1. Try @lat,lng
        val atRegex = Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        val atMatch = atRegex.find(url)
        if (atMatch != null) {
            val lat = atMatch.groupValues[1].toDoubleOrNull()
            val lng = atMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 2. Try !3d!4d Custom maps serialize formats
        val d3d4Regex = Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)")
        val d3d4Match = d3d4Regex.find(url)
        if (d3d4Match != null) {
            val lat = d3d4Match.groupValues[1].toDoubleOrNull()
            val lng = d3d4Match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 3. Try standard query q= or query=
        val qRegex = Regex("[?&](?:q|query|ll)=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        val qMatch = qRegex.find(url)
        if (qMatch != null) {
            val lat = qMatch.groupValues[1].toDoubleOrNull()
            val lng = qMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        return null
    }

    fun parseSharedText(text: String): SharedLocation {
        val trimmed = text.trim()
        val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.isEmpty()) {
            return SharedLocation(originalText = text)
        }

        val url = extractUrl(trimmed) ?: ""
        var placeName = lines[0]
        
        if (placeName.startsWith("http")) {
            placeName = decodePlaceNameFromUrl(placeName) ?: "Shared Location"
        }

        val addressLines = lines.filter { line ->
            line != placeName && !line.startsWith("http") && !line.contains("maps.app.goo.gl")
        }
        val address = addressLines.joinToString(", ")

        return SharedLocation(
            originalText = text,
            placeName = placeName,
            googlePlaceName = placeName,
            address = address,
            googleMapsUrl = url
        )
    }

    /**
     * South Korea lat/lng bounds for warning check
     */
    fun isLatLngInKorea(lat: Double, lng: Double): Boolean {
        // Approx South Korea bounding box
        return lat in 33.0..39.0 && lng in 124.0..132.0
    }
}
