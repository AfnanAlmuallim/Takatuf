package com.z.takatuf.data

import com.z.takatuf.data.ai.OpenAiRoles
import com.z.takatuf.data.model.ListingKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The OpenAI request the editor sends and how the reply becomes role suggestions. */
class OpenAiRolesTest {

    /** A Responses API reply as OpenAI returns it: reasoning first, then the message. */
    private fun reply(rolesJson: String): String = JSONObject()
        .put("id", "resp_test")
        .put("object", "response")
        .put(
            "output",
            JSONArray()
                .put(JSONObject().put("type", "reasoning").put("summary", JSONArray()))
                .put(
                    JSONObject().put("type", "message").put("role", "assistant").put(
                        "content",
                        JSONArray().put(JSONObject().put("type", "output_text").put("text", rolesJson).put("annotations", JSONArray())),
                    ),
                ),
        )
        .toString()

    @Test
    fun `reads roles from the reply and keeps them within the app's limits`() {
        val roles = OpenAiRoles.parse(
            reply(
                """
                {"roles":[
                  {"title":"Android developer","description":"Builds the live map.","skills":["Kotlin","Kotlin"," Maps API ",""],"openings":1},
                  {"title":"Backend developer","description":"Stores sensor readings.","skills":["Python","SQL"],"openings":9},
                  {"title":"  ","description":"No title, so dropped.","skills":["x"],"openings":1},
                  {"title":"Usability lead","description":"Runs the trial.","skills":["User research"],"openings":0}
                ]}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("Android developer", "Backend developer", "Usability lead"), roles.map { it.title })
        assertEquals(listOf("Kotlin", "Maps API"), roles[0].skills)
        assertEquals(4, roles[1].openings)
        assertEquals(1, roles[2].openings)
    }

    @Test
    fun `a refusal or an empty answer gives no roles rather than an error`() {
        val refusal = JSONObject().put(
            "output",
            JSONArray().put(
                JSONObject().put("type", "message").put(
                    "content",
                    JSONArray().put(JSONObject().put("type", "refusal").put("refusal", "I can't help with that.")),
                ),
            ),
        ).toString()
        assertTrue(OpenAiRoles.parse(refusal).isEmpty())
        assertTrue(OpenAiRoles.parse("""{"output":[]}""").isEmpty())
        assertTrue(OpenAiRoles.parse(reply("""{"roles":[]}""")).isEmpty())
    }

    @Test
    fun `the request asks gpt-6-luna for strict JSON and carries only what the student wrote`() {
        val prompt = OpenAiRoles.prompt(
            ListingKind.Team,
            title = "فريق هاكاثون المدينة الذكية",
            outcome = "نموذج أولي لتطبيق مسارات المشي المظللة",
            description = "",
            eventTitle = "Najd Smart City Hackathon",
        )
        assertTrue(prompt.startsWith("Type: competition team"))
        assertTrue(prompt.contains("Event: Najd Smart City Hackathon"))
        assertTrue(prompt.contains("Title: فريق هاكاثون المدينة الذكية"))
        assertFalse(prompt.contains("Details:"))

        val request = OpenAiRoles.request("gpt-6-luna", prompt)
        assertEquals("gpt-6-luna", request.getString("model"))
        val format = request.getJSONObject("text").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getBoolean("strict"))
        val input = request.getJSONArray("input")
        assertEquals("system", input.getJSONObject(0).getString("role"))
        assertEquals(prompt, input.getJSONObject(1).getString("content"))
    }
}
