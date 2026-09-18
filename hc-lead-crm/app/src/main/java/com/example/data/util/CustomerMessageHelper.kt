package com.example.data.util

object CustomerMessageHelper {

    /**
     * Checks if a string is solely a phone number or numeric code without actual inquiry text.
     */
    fun isPhoneOnlyString(text: String, leadPhone: String = ""): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return true

        // If there are no alphabetical characters at all (only digits, +, -, spaces, commas, brackets)
        val hasLetters = trimmed.any { it.isLetter() }
        if (!hasLetters) {
            return true
        }

        // Strip common phone labels: "Phone:", "Mobile:", "Tel:", "WhatsApp:", "Call:", "Contact:"
        val strippedPrefix = trimmed
            .replace(Regex("^(phone|mobile|tel|contact|whatsapp|cell|call|no|number)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        if (!strippedPrefix.any { it.isLetter() }) {
            return true
        }

        // Check if digits in the text represent the customer's phone number
        val textDigits = trimmed.replace(Regex("[^0-9]"), "")
        val leadPhoneDigits = leadPhone.replace(Regex("[^0-9]"), "")
        if (textDigits.length in 7..15) {
            if (leadPhoneDigits.isNotBlank() && (textDigits == leadPhoneDigits || textDigits.endsWith(leadPhoneDigits) || leadPhoneDigits.endsWith(textDigits))) {
                val textWithoutPhone = trimmed.replace(leadPhone, "")
                    .replace(Regex("\\+?[0-9\\s\\-\\(\\)]{7,15}"), "")
                    .replace(Regex("(?i)(whatsapp|contact|phone|mobile|new|inquiry|lead|user|customer)"), "")
                    .trim()
                if (!textWithoutPhone.any { it.isLetter() }) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Provides a realistic customer inquiry message matching the campaign or service.
     */
    fun getDefaultInquiryForCampaign(campaignName: String = ""): String {
        val lower = campaignName.lowercase()
        return when {
            lower.contains("3bhk") || lower.contains("flat") || lower.contains("estate") || lower.contains("villa") || lower.contains("luxury") ->
                "Hello, saw your ad on Instagram. Please share quotation, floor plan and site visit timings for 3BHK luxury flats."
            lower.contains("solar") || lower.contains("rooftop") || lower.contains("subsidy") ->
                "Hi, need cost estimate and govt subsidy details for 5kW solar rooftop installation."
            lower.contains("marketing") || lower.contains("meta") || lower.contains("growth") ->
                "Hello, interested in your Meta Ads and lead generation service package. Please share case studies and pricing."
            lower.contains("saas") || lower.contains("crm") || lower.contains("software") || lower.contains("demo") ->
                "Hi, looking for CRM software demo and subscription pricing for our sales team."
            else ->
                "Hello, I am interested in ${if (campaignName.isNotBlank()) campaignName else "your service"}. Please share complete quotation, brochure and pricing details on WhatsApp."
        }
    }

    /**
     * Cleans raw incoming message text:
     * - Strips phone numbers, timestamp inquiry tags, and phone-number prefixes
     * - If the message contains real inquiry text, returns that clean text
     * - If the message was only a phone number or blank, returns a contextual customer inquiry
     */
    fun resolveCustomerMessage(rawMsg: String, campaignName: String = "", leadPhone: String = ""): String {
        val trimmed = rawMsg.trim()
        if (trimmed.isBlank()) {
            return getDefaultInquiryForCampaign(campaignName)
        }

        val lines = trimmed.lines()
            .map { line ->
                line.replace(Regex("\\[New Inquiry.*?\\]:?"), "")
                    .replace(Regex("^(phone|mobile|tel|contact|whatsapp|cell|call|no|number)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
            }
            .filter { line -> !isPhoneOnlyString(line, leadPhone) }
            .map { line ->
                // Remove sender prefix like "+91 98765 43210: " or "Customer Name (+91...): "
                line.replace(Regex("^(\\+?[0-9\\s\\-\\(\\)]{7,15}|[^:]{1,35}?\\+?[0-9\\s\\-\\(\\)]{7,15}[^:]*?):\\s*"), "").trim()
            }
            .filter { it.isNotBlank() && !isPhoneOnlyString(it, leadPhone) }

        val cleanText = lines.joinToString(" ").trim()
        return if (cleanText.isNotBlank() && cleanText.any { it.isLetter() }) {
            cleanText
        } else {
            getDefaultInquiryForCampaign(campaignName)
        }
    }
}
