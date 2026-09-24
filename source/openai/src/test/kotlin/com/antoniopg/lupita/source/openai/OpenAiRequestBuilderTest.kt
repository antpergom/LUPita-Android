package com.antoniopg.lupita.source.openai

import com.antoniopg.lupita.core.model.Depth
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiRequestBuilderTest {
    @Test
    fun `the model and both roles are present`() {
        val body = OpenAiRequestBuilder.build(AnalysisRequest("hola", depth = Depth.LOW), model = "gpt-6-luna")

        assertEquals("gpt-6-luna", body["model"]?.jsonPrimitive?.content)
        val input = body["input"]!!.jsonArray
        assertEquals("system", input[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", input[1].jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the user text is carried through as input_text`() {
        val body = OpenAiRequestBuilder.build(AnalysisRequest("el texto normalizado", depth = Depth.LOW), model = "m")

        val userContent = body["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("input_text", userContent[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("el texto normalizado", userContent[0].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `without an image only the text part is sent`() {
        val body = OpenAiRequestBuilder.build(AnalysisRequest("solo texto", depth = Depth.LOW), model = "m")

        val userContent = body["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(1, userContent.size)
    }

    @Test
    fun `an image is sent as a base64 data URL`() {
        val body = OpenAiRequestBuilder.build(
            AnalysisRequest("con imagen", imageWebpBase64 = "QUJD", depth = Depth.LOW),
            model = "m",
        )

        val userContent = body["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(2, userContent.size)
        val imagePart = userContent[1].jsonObject
        assertEquals("input_image", imagePart["type"]?.jsonPrimitive?.content)
        assertEquals("data:image/webp;base64,QUJD", imagePart["image_url"]?.jsonPrimitive?.content)
    }

    @Test
    fun `depth maps to reasoning effort one to one`() {
        assertEquals(
            "low",
            OpenAiRequestBuilder.build(AnalysisRequest("t", depth = Depth.LOW), "m")["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "medium",
            OpenAiRequestBuilder.build(AnalysisRequest("t", depth = Depth.MEDIUM), "m")["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "high",
            OpenAiRequestBuilder.build(AnalysisRequest("t", depth = Depth.HIGH), "m")["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `a custom prompt replaces the default system text`() {
        val body = OpenAiRequestBuilder.build(AnalysisRequest("t", depth = Depth.LOW), "m", prompt = "prompt a medida")

        val systemContent = body["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("prompt a medida", systemContent[0].jsonObject["text"]?.jsonPrimitive?.content)
    }
}
