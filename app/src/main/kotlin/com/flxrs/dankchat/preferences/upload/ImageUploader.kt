package com.flxrs.dankchat.preferences.upload

data class ImageUploader(
    var uploadUrl: String,
    var formField: String,
    var headers: String?,
    var imageLinkPattern: String?,
    var deletionLinkPattern: String?
) {
    val parsedHeaders: List<Pair<String, String>>
        get() = headers
            ?.split(";")
            ?.mapNotNull {
                val splits = it.split(":", limit = 2)
                when {
                    splits.size != 2 -> null
                    else             -> Pair(splits[0].trim(), splits[1].trim())
                }
            }.orEmpty()
}