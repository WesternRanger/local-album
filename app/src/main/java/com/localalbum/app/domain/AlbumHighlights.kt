package com.localalbum.app.domain

/**
 * Popular 感想 and emoji drawn from the user's own comments on photos in one album.
 * Likes only raise the rank of an existing 感想. They never invent text.
 */
data class AlbumHighlights(
    val thoughts: List<String> = emptyList(),
    val emoji: List<String> = emptyList(),
) {
    val hasContent: Boolean get() = thoughts.isNotEmpty() || emoji.isNotEmpty()

    companion object {
        private class Score(var weight: Int, var latest: Long, val order: Int)

        private val emojiToken = Regex(
            "\\p{So}(?:\\uFE0F|\\uFE0E)?(?:\\u200D\\p{So}(?:\\uFE0F|\\uFE0E)?)*",
        )

        fun emojiIn(text: String): List<String> = emojiToken.findAll(text).map { it.value }.distinct().toList()

        fun thoughtText(text: String): String = emojiToken.replace(text, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        fun fromAlbum(
            album: Album,
            comments: Map<String, List<Comment>>,
            likes: Set<String>,
            thoughtLimit: Int,
            emojiLimit: Int = 3,
        ): AlbumHighlights {
            val thoughts = LinkedHashMap<String, Score>()
            val symbols = LinkedHashMap<String, Score>()
            var order = 0
            for (item in album.items) {
                val boost = if (item.key in likes) 1 else 0
                for (comment in comments[item.key].orEmpty()) {
                    if (comment.body.isBlank()) continue
                    val weight = 1 + boost
                    val text = thoughtText(comment.body)
                    if (text.isNotEmpty()) {
                        val score = thoughts.getOrPut(text) { Score(0, 0L, order++) }
                        score.weight += weight
                        if (comment.createdAt >= score.latest) score.latest = comment.createdAt
                    }
                    for (symbol in emojiIn(comment.body)) {
                        val score = symbols.getOrPut(symbol) { Score(0, 0L, order++) }
                        score.weight += weight
                        if (comment.createdAt >= score.latest) score.latest = comment.createdAt
                    }
                }
            }
            return AlbumHighlights(
                thoughts = rank(thoughts, thoughtLimit),
                emoji = rank(symbols, emojiLimit),
            )
        }

        private fun rank(scores: Map<String, Score>, limit: Int): List<String> {
            if (limit <= 0) return emptyList()
            return scores.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Score>> { it.value.weight }
                        .thenByDescending { it.value.latest }
                        .thenBy { it.value.order },
                )
                .take(limit)
                .map { it.key }
        }
    }
}
