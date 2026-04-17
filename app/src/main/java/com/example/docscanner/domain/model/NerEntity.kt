// domain/model/NerEntity.kt
package com.example.docscanner.domain.model

data class NerEntity(
    val type: NerType,
    val text: String,
    val startChar: Int,
    val endChar: Int,
    val confidence: Float,
) {
    val length: Int get() = endChar - startChar
}

enum class NerType {
    PERSON,
    LOCATION,
    ORGANIZATION,
    DATE,
    MISC,
}