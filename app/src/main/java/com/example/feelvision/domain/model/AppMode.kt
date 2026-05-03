package com.feelvision.domain.model

enum class AppMode(
    val id: Int,
    val displayName: String,
    val shortLabel: String,
    val ttsAnnouncement: String
) {
    Default(0, "Default",     "DEF", "Default mode. Press capture to describe surroundings."),
    OCR    (1, "Read Text",   "OCR", "Read text mode."),
    Navigate(2,"Navigate",    "NAV", "Navigation mode."),
    Face   (3, "People",      "PPL", "People recognition mode."),
    Currency(4,"Currency",    "CUR", "Currency detection mode."),
    Edu    (5, "Educational", "EDU", "Educational mode."),
    Narrate(6, "Narration",   "NAR", "Narration mode.");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: Default
        fun next(current: AppMode) = fromId((current.id + 1) % entries.size)
        fun prev(current: AppMode) = fromId(((current.id - 1) + entries.size) % entries.size)
    }

    fun getLocalizedAnnouncement(language: String): String {
        return when (language.lowercase()) {
            "hindi" -> when (this) {
                Default -> "डिफ़ॉल्ट मोड। आस-पास का वर्णन करने के लिए कैप्चर दबाएं।"
                OCR -> "टेक्स्ट पढ़ें मोड।"
                Navigate -> "नेविगेशन मोड।"
                Face -> "लोग पहचान मोड।"
                Currency -> "मुद्रा पहचान मोड।"
                Edu -> "शैक्षिक मोड।"
                Narrate -> "वर्णन मोड।"
            }
            "telugu" -> when (this) {
                Default -> "డిఫాల్ట్ మోడ్. పరిసరాలను వివరించడానికి క్యాప్చర్ నొక్కండి."
                OCR -> "టెక్స్ట్ చదవండి మోడ్."
                Navigate -> "నావిగేషన్ మోడ్."
                Face -> "వ్యక్తుల గుర్తింపు మోడ్."
                Currency -> "కరెన్సీ గుర్తింపు మోడ్."
                Edu -> "విద్యా మోడ్."
                Narrate -> "వ్యాఖ్యాన మోడ్."
            }
            "tamil" -> when (this) {
                Default -> "இயல்புநிலை பயன்முறை. சுற்றியுள்ளவற்றை விவரிக்க கேப்சரை அழுத்தவும்."
                OCR -> "உரை படிக்கும் பயன்முறை."
                Navigate -> "வழிசெலுத்தல் பயன்முறை."
                Face -> "மக்கள் அங்கீகார பயன்முறை."
                Currency -> "நாணய கண்டறிதல் பயன்முறை."
                Edu -> "கல்வி பயன்முறை."
                Narrate -> "விவரிப்பு பயன்முறை."
            }
            "kannada" -> when (this) {
                Default -> "ಡೀಫಾಲ್ಟ್ ಮೋಡ್. ಸುತ್ತಮುತ್ತಲಿನ ಪ್ರದೇಶವನ್ನು ವಿವರಿಸಲು ಕ್ಯಾಪ್ಚರ್ ಒತ್ತಿರಿ."
                OCR -> "ಪಠ್ಯ ಓದುವ ಮೋಡ್."
                Navigate -> "ನ್ಯಾವಿಗೇಷನ್ ಮೋಡ್."
                Face -> "ಜನರ ಗುರುತಿಸುವಿಕೆ ಮೋಡ್."
                Currency -> "ಕರೆನ್ಸಿ ಗುರುತಿಸುವಿಕೆ ಮೋಡ್."
                Edu -> "ಶೈಕ್ಷಣಿಕ ಮೋಡ್."
                Narrate -> "ನಿರೂಪಣೆ ಮೋಡ್."
            }
            "malayalam" -> when (this) {
                Default -> "ഡിഫോൾട്ട് മോഡ്. ചുറ്റുപാടുകൾ വിവരിക്കാൻ ക്യാപ്ചർ അമർത്തുക."
                OCR -> "വാചകം വായിക്കുന്ന മോഡ്."
                Navigate -> "നാവിഗേഷൻ മോഡ്."
                Face -> "ആളുകളെ തിരിച്ചറിയുന്ന മോഡ്."
                Currency -> "കറൻസി കണ്ടെത്തുന്ന മോഡ്."
                Edu -> "വിദ്യാഭ്യാസ മോഡ്."
                Narrate -> "വിവരണ മോഡ്."
            }
            else -> ttsAnnouncement
        }
    }
}