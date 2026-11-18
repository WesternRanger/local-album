package com.localalbum.app.domain

data class SimilarGroup(
    val id: String,
    val items: List<MediaItem>,
)

/**
 * Groups near-duplicate still photos by perceptual-hash distance.
 * Videos are ignored. A photo with no hash is ignored. Groups smaller than two are dropped.
 */
object SimilarPhotos {
    const val MAX_DISTANCE = 8

    fun group(
        items: List<MediaItem>,
        hashes: Map<String, Long>,
        maxDistance: Int = MAX_DISTANCE,
    ): List<SimilarGroup> {
        val photos = items.filter { it.kind == MediaKind.PHOTO && hashes[it.key] != null }
        if (photos.size < 2) return emptyList()
        val parent = IntArray(photos.size) { it }

        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
        }

        for (left in photos.indices) {
            val leftHash = hashes.getValue(photos[left].key)
            for (right in left + 1 until photos.size) {
                val distance = DHash.hamming(leftHash, hashes.getValue(photos[right].key))
                if (distance <= maxDistance) union(left, right)
            }
        }

        return photos.indices
            .groupBy { find(it) }
            .values
            .map { indexes -> indexes.map { photos[it] } }
            .filter { it.size >= 2 }
            .map { members ->
                val ordered = members.sortedWith(
                    compareByDescending<MediaItem> { it.dateTakenMillis ?: Long.MIN_VALUE }
                        .thenBy { it.key },
                )
                SimilarGroup(
                    id = ordered.joinToString(separator = "|") { it.key },
                    items = ordered,
                )
            }
            .sortedWith(
                compareByDescending<SimilarGroup> { group ->
                    group.items.maxOf { it.dateTakenMillis ?: Long.MIN_VALUE }
                }.thenBy { it.id },
            )
    }
}
