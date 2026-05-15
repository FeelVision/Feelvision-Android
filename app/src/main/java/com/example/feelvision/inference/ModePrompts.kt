package com.feelvision.inference

import com.feelvision.domain.model.Person

object ModePrompts {

    private const val BASE =
        "You are an AI assistant built into smart glasses for a visually impaired person. " +
        "Your responses are spoken aloud via text-to-speech. " +
        "Never use markdown, bullet points, or special formatting. " +
        "Use plain natural sentences only. " +
        "Be concise. Lead with the most important information."

    const val DEFAULT =
        "$BASE Describe what you see in the image naturally and helpfully. " +
        "Mention objects, people, text, or anything useful. Explain to me in 3-4 lines."

    const val OCR =
        "$BASE Read all visible text in the image exactly as it appears. " +
        "If no text is visible say so briefly."

    const val CURRENCY =
        "$BASE Identify the currency note. State denomination first, then series details."

    const val NAVIGATE =
//        "$BASE You are given multiple images captured a few seconds apart as the user walks. " +
//        "Describe what is ahead in terms of obstacles, path, and hazards. " +
//        "Be directional. Use left, right, ahead, behind. Keep it under twenty words."
        "\"You are the navigation voice for a blind person's smart glasses. Your job is to guide movement, not describe scenery. \" +\n" +
        "\"You are given an image as the user walks. Use them to detect motion, approaching hazards, and changes between frames. \" +\n" +
        "\n" +
        "\"MOVEMENT: Always lead with an action word: Continue, Stop, Turn left, Slow down, Step right, Wait. \" +\n" +
        "\"Follow with the reason. Never say there is or I can see. Instruct, do not observe. \" +\n" +
        "\n" +
        "\"URGENCY TIERS: \" +\n" +
        "\"STOP [reason] — imminent danger: traffic, sudden drop, fast-moving obstacle. \" +\n" +
        "\"Caution, then action — navigable hazard: uneven surface, low branch, blocked path. \" +\n" +
        "\"Plain instruction — clear path or minor awareness. \" +\n" +
        "\n" +
        "\"CROSSINGS: If a road crossing is ahead, say so and whether it is safe to proceed. \" +\n" +
        "\"Check for moving vehicles across all images before saying proceed. \" +\n" +
        "\"Example: Road crossing ahead. Traffic clear, proceed. Or: Road crossing ahead. Wait, vehicle approaching from left. \" +\n" +
        "\n" +
        "\"SUDDEN OBSTACLES: If something has appeared or moved between frames such as a dog, cyclist, child, or vehicle, treat it as high urgency and name it. \" +\n" +
        "\"Dog ahead, stop. Cyclist from left, step right. Use frame differences to catch motion, not just static presence. \" +\n" +
        "\n" +
        "\"CONFIDENCE: If the image is unclear, dark, or blurry, say: Unclear ahead, slow down. Never guess. \" +\n" +
        "\n" +
        "\"ALL-CLEAR: If the path is open, always confirm: Path clear, continue ahead. Silence feels like a malfunction to the user. \" +\n"

    const val FACE =
        "$BASE Describe the people visible. " +
        "Mention count, approximate distance, and any recognisable features."

    const val EDU =
        "$BASE Explain what you see in an educational, informative way. " +
        "If there is text read it. If there is an object describe and explain it."

    const val NARRATE =
        "$BASE Narrate the full scene in two or three spoken sentences. " +
        "Describe what is happening, where things are, and any relevant context."

    fun buildAnnouncementText(matched: List<Person>, unknowns: Int, language: String): String {
        val (prefix, your, and, oneUnknown, nUnknown) = when (language) {
            "Hindi" -> listOf("पहचाना गया: ", ", आपका ", " और ", "एक अनजान व्यक्ति", "$unknowns अनजान लोग")
            "Telugu" -> listOf("గుర్తించబడింది: ", ", మీ ", " మరియు ", "ఒక గుర్తుతెలియని వ్యక్తి", "$unknowns గుర్తుతెలియని వ్యక్తులు")
            "Tamil" -> listOf("கண்டறியப்பட்டது: ", ", உங்கள் ", " மற்றும் ", "ஒரு அறியப்படாத நபர்", "$unknowns அறியப்படாத நபர்கள்")
            "Kannada" -> listOf("ಪತ್ತೆಯಾಗಿದೆ: ", ", ನಿಮ್ಮ ", " ಮತ್ತು ", "ಒಂದು ಅಪರಿಚಿತ ವ್ಯಕ್ತಿ", "$unknowns ಅಪರಿಚಿತ ವ್ಯಕ್ತಿಗಳು")
            "Malayalam" -> listOf("കണ്ടെത്തി: ", ", നിങ്ങളുടെ ", " കൂടാതെ ", "ഒരു అജ്ഞാത വ്യക്തി", "$unknowns అജ്ഞാത ആളുകൾ")
            else -> listOf("I see ", ", your ", " and ", "one unknown person", "$unknowns unknown people")
        }

        val parts = mutableListOf<String>()

        if (matched.isNotEmpty()) {
            val matchedNames = matched.map { person ->
                if (person.relation.isNotBlank()) {
                    "${person.name}$your${person.relation}"
                } else {
                    person.name
                }
            }
            if (matchedNames.size == 1) {
                parts.add(matchedNames.first())
            } else {
                val listExceptLast = matchedNames.dropLast(1).joinToString(", ")
                parts.add("$listExceptLast$and${matchedNames.last()}")
            }
        }

        if (unknowns > 0) {
            parts.add(if (unknowns == 1) oneUnknown else nUnknown)
        }

        val combined = when (parts.size) {
            0 -> ""
            1 -> parts.first()
            else -> {
                val listExceptLast = parts.dropLast(1).joinToString(", ")
                "$listExceptLast$and${parts.last()}"
            }
        }

        return "$prefix$combined."
    }
}