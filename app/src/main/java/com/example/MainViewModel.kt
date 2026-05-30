package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.GoogleMapsParser
import com.example.util.SharedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _sharedText = MutableStateFlow("")
    val sharedText: StateFlow<String> = _sharedText.asStateFlow()

    private val _parsedLocation = MutableStateFlow<SharedLocation?>(null)
    val parsedLocation: StateFlow<SharedLocation?> = _parsedLocation.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _isNaverInstalled = MutableStateFlow(false)
    val isNaverInstalled: StateFlow<Boolean> = _isNaverInstalled.asStateFlow()

    fun checkNaverInstalled(context: Context) {
        val installed = try {
            context.packageManager.getPackageInfo("com.nhn.android.nmap", 0)
            true
        } catch (e: Exception) {
            false
        }
        _isNaverInstalled.value = installed
    }

    fun handleReceivedText(text: String) {
        if (text.isBlank()) return
        _sharedText.value = text
        _isResolving.value = true
        _parsedLocation.value = null

        viewModelScope.launch {
            try {
                // 1. Basic parsing from original shared text
                val initialLocation = GoogleMapsParser.parseSharedText(text)
                
                if (initialLocation.googleMapsUrl.isNotEmpty()) {
                    // 2. Resolve short Google Maps URL (follows redirects and retrieves HTML metadata)
                    val resolvedInfo = GoogleMapsParser.resolveShortUrl(initialLocation.googleMapsUrl)
                    if (resolvedInfo != null) {
                        val resolvedUrl = resolvedInfo.resolvedUrl
                        // 3. Parse coordinates from resolved URL
                        val coords = GoogleMapsParser.extractCoordinates(resolvedUrl)
                        val lat = coords?.first
                        val lng = coords?.second
                        val inKorea = if (lat != null && lng != null) GoogleMapsParser.isLatLngInKorea(lat, lng) else false
                        
                        // Extract clean Korean name from metadata
                        val koreanName = resolvedInfo.ogTitle
                        // Extract clean Korean address from metadata description segment
                        val koreanAddress = resolvedInfo.ogDescription?.let {
                            GoogleMapsParser.extractAddressFromDescription(it)
                        }

                        val decodedName = GoogleMapsParser.decodePlaceNameFromUrl(resolvedUrl)
                        
                        val finalName = when {
                            !koreanName.isNullOrBlank() -> koreanName
                            initialLocation.placeName != "Shared Location" && initialLocation.placeName.isNotEmpty() && !initialLocation.placeName.startsWith("http") -> initialLocation.placeName
                            !decodedName.isNullOrBlank() -> decodedName
                            else -> "Shared Location"
                        }

                        val finalAddress = if (!koreanAddress.isNullOrBlank()) {
                            koreanAddress
                        } else {
                            initialLocation.address
                        }

                        val googlePlaceName = when {
                            initialLocation.placeName != "Shared Location" && initialLocation.placeName.isNotEmpty() && !initialLocation.placeName.startsWith("http") -> initialLocation.placeName
                            !decodedName.isNullOrBlank() -> decodedName
                            else -> "Shared Location"
                        }

                        _parsedLocation.value = initialLocation.copy(
                            placeName = finalName,
                            googlePlaceName = googlePlaceName,
                            address = finalAddress,
                            resolvedUrl = resolvedUrl,
                            latitude = lat,
                            longitude = lng,
                            isKoreanLocation = inKorea
                        )
                    } else {
                        // If resolve failed, keep the initial text details
                        _parsedLocation.value = initialLocation
                    }
                } else {
                    // No URL, just text. Try to see if this raw text has coordinates inside it
                    val coords = GoogleMapsParser.extractCoordinates(text)
                    val lat = coords?.first
                    val lng = coords?.second
                    val inKorea = if (lat != null && lng != null) GoogleMapsParser.isLatLngInKorea(lat, lng) else false

                    _parsedLocation.value = initialLocation.copy(
                        latitude = lat,
                        longitude = lng,
                        isKoreanLocation = inKorea
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isResolving.value = false
            }
        }
    }

    fun clearState() {
        _sharedText.value = ""
        _parsedLocation.value = null
        _isResolving.value = false
    }

    fun updatePlaceName(name: String) {
        _parsedLocation.value = _parsedLocation.value?.copy(placeName = name)
    }

    fun updateAddress(address: String) {
        _parsedLocation.value = _parsedLocation.value?.copy(address = address)
    }

    /**
     * Helper to clean up names or queries for Naver Map search and route names.
     * Extracts only Korean (Hangul) characters, spaces, and digits if the input contained Hangul.
     * If no Hangul is detected, returns the original string as fallback.
     */
    private fun cleanNameForNaver(input: String): String {
        if (input.isBlank()) return input

        // Helper to check if a character belongs to any Hangul ranges
        fun isHangul(c: Char): Boolean {
            val codePoint = c.code
            return (codePoint in 0xAC00..0xD7A3) || // Hangul Syllables
                   (codePoint in 0x1100..0x11FF) || // Hangul Jamo
                   (codePoint in 0x3130..0x318F) || // Hangul Compatibility Jamo
                   (codePoint in 0xA960..0xA97F) || // Hangul Jamo Extended-A
                   (codePoint in 0xD7B0..0xD7FF)    // Hangul Jamo Extended-B
        }

        // Check if the input string contains any Hangul characters
        var hasHangul = false
        for (char in input) {
            if (isHangul(char)) {
                hasHangul = true
                break
            }
        }

        // If it contains Korean, keep only Hangul characters, digits, and spaces
        if (hasHangul) {
            val sb = StringBuilder()
            for (char in input) {
                if (isHangul(char) || char.isDigit() || char == ' ') {
                    sb.append(char)
                }
            }
            val cleaned = sb.toString().replace(Regex("\\s+"), " ").trim()
            if (cleaned.isNotEmpty()) {
                return cleaned
            }
        }

        // Fallback to original input if no Korean or if cleaned string is empty
        return input
    }

    /**
     * Builds URI schemes for Naver Map
     * Mode: "car" (Navigation/Driving), "walk" (Walking), "bus" (Transit)
     */
    fun launchNaverMap(context: Context, mode: String = "car") {
        val location = _parsedLocation.value ?: return
        val urlScheme: String
        
        if (location.latitude != null && location.longitude != null) {
            // Direct Route Scheme
            // Format: nmap://route/{mode}?dlat={dlat}&dlng={dlng}&dname={dname}&appname=com.example
            val cleanedName = cleanNameForNaver(location.placeName)
            val encodedName = Uri.encode(cleanedName)
            urlScheme = "nmap://route/$mode?dlat=${location.latitude}&dlng=${location.longitude}&dname=$encodedName&appname=${context.packageName}"
        } else {
            // Text Search Scheme
            // Format: nmap://search?query={query}&appname=com.example
            val rawQuery = location.placeName.ifEmpty { location.address }
            val cleanedQuery = cleanNameForNaver(rawQuery)
            val queryParam = Uri.encode(cleanedQuery)
            urlScheme = "nmap://search?query=$queryParam&appname=${context.packageName}"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlScheme)).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: If Naver Map scheme fails, open Web fallback, or open Play Store
            openWebFallback(context, location)
        }
    }

    fun openPlayStoreForNaver(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.nhn.android.nmap")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.nhn.android.nmap")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    private fun openWebFallback(context: Context, location: SharedLocation) {
        val webUrl = if (location.latitude != null && location.longitude != null) {
            // Web view of specific coordinate
            "https://map.naver.com/v5/entry/address/${location.latitude},${location.longitude}"
        } else {
            // Web search fallback
            val rawQuery = location.placeName.ifEmpty { location.address }
            val cleanedQuery = cleanNameForNaver(rawQuery)
            val querySegment = Uri.encode(cleanedQuery)
            "https://map.naver.com/v5/search/$querySegment"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
