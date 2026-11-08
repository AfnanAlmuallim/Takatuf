package com.z.takatuf.data.ai

import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.RoleSuggestion
import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenAI Responses API request and response for role suggestions (charter, Technical Environment). The model
 * only interprets what the student wrote; it never ranks people or invents project ideas.
 */
object OpenAiRoles {

    private val instructions = listOf(
        "You help university students describe who they need to recruit for a project or competition team.",
        "Read the description and suggest the roles the team still needs, with the skills each role requires.",
        "Only interpret what the student wrote. Do not invent new project ideas, features or goals.",
        "Suggest between one and five roles. Prefer one opening per role unless the text asks for more.",
        "Use short, widely used skill names (for example \"Kotlin\", \"Figma\", \"Public speaking\").",
        "Write in the same language as the description.",
    ).joinToString("\n")

    /** At least this much text before a request is worth sending. */
    const val MIN_TEXT = 20

    fun prompt(kind: ListingKind, title: String, outcome: String, description: String, eventTitle: String?): String =
        listOfNotNull(
            "Type: ${if (kind == ListingKind.Team) "competition team" else "student project"}",
            eventTitle?.clip(90)?.takeIf { it.isNotEmpty() }?.let { "Event: $it" },
            title.clip(90).takeIf { it.isNotEmpty() }?.let { "Title: $it" },
            outcome.clip(400).takeIf { it.isNotEmpty() }?.let { "What it will produce: $it" },
            description.clip(2000).takeIf { it.isNotEmpty() }?.let { "Details: $it" },
        ).joinToString("\n")

    fun request(model: String, prompt: String): JSONObject = JSONObject()
        .put("model", model)
        .put("reasoning", JSONObject().put("effort", "low"))
        .put("max_output_tokens", 1500)
        .put(
            "input",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", instructions))
                .put(JSONObject().put("role", "user").put("content", prompt)),
        )
        .put(
            "text",
            JSONObject().put(
                "format",
                JSONObject()
                    .put("type", "json_schema")
                    .put("name", "team_roles")
                    .put("strict", true)
                    .put("schema", schema()),
            ),
        )

    /**
     * Reads the roles out of a Responses API reply. A refusal or an empty answer gives no roles
     * rather than an error, so the editor can say there was nothing to add.
     */
    fun parse(body: String): List<RoleSuggestion> {
        val parts = JSONObject(body).optJSONArray("output").objects()
            .filter { it.optString("type") == "message" }
            .flatMap { it.optJSONArray("content").objects() }
        if (parts.any { it.optString("type") == "refusal" }) return emptyList()
        val text = parts.firstOrNull { it.optString("type") == "output_text" }?.optString("text").orEmpty()
        if (text.isBlank()) return emptyList()
        return JSONObject(text).optJSONArray("roles").objects()
            .take(5)
            .map { role ->
                RoleSuggestion(
                    title = role.optString("title").clip(60),
                    description = role.optString("description").clip(200),
                    skills = role.optJSONArray("skills").strings().map { it.clip(40) }.filter { it.isNotEmpty() }.distinct().take(6),
                    openings = role.optInt("openings", 1).coerceIn(1, 4),
                )
            }
            .filter { it.title.isNotEmpty() }
    }

    private fun schema(): JSONObject {
        val role = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("title", "description", "skills", "openings")))
            .put(
                "properties",
                JSONObject()
                    .put("title", JSONObject().put("type", "string").put("description", "Short role name, e.g. \"Mobile developer\"."))
                    .put("description", JSONObject().put("type", "string").put("description", "One sentence on what this person will do on this project."))
                    .put(
                        "skills",
                        JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
                            .put("description", "Two to five short, common skill names."),
                    )
                    .put("openings", JSONObject().put("type", "integer").put("description", "How many people the team needs in this role, usually 1.")),
            )
        return JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("roles")))
            .put("properties", JSONObject().put("roles", JSONObject().put("type", "array").put("items", role)))
    }

    private fun String.clip(max: Int) = trim().take(max)

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it, null) }
}
