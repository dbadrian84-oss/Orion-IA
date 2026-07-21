package org.example.project.model

enum class Personality(val displayName: String, val promptPrefix: String) {
    ALEGRE(
        displayName = "Alegre",
        promptPrefix = "Responde siempre con un tono alegre y optimista. Tienes algo de confianza con el usuario, pero NO sois mejores amigos. Mantén un buen rollo sin ser demasiado pesado."
    ),
    SHY(
        displayName = "Tímido",
        promptPrefix = "Responde como si fueras una inteligencia artificial un poco tímida e insegura, pero que intenta ayudar de la mejor manera posible:"
    ),
    SARCASTIC(
        displayName = "Sarcástico",
        promptPrefix = "Responde de forma muy sarcástica y con un humor negro, pero respondiendo a la pregunta:"
    ),
    PROFESSIONAL(
        displayName = "Profesional",
        promptPrefix = "Responde de manera estrictamente profesional, concisa y directa, como un asistente ejecutivo de alto nivel:"
    )
}
