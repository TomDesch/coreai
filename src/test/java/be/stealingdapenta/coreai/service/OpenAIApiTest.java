package be.stealingdapenta.coreai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class OpenAIApiTest {

    @Test
    void parsesStructuredErrorBodies() {
        OpenAiException exception = OpenAIApi.OPEN_AI_API.parseOpenAiError(401,
                "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"Bad key\"}}");

        assertNotNull(exception);
        assertEquals(401, exception.getStatus());
        assertEquals("invalid_api_key", exception.getCode());
        assertEquals("Bad key", exception.getMessage());
    }

    @Test
    void fallsBackToGenericHttpErrorWhenBodyIsNotStructured() {
        OpenAiException exception = OpenAIApi.OPEN_AI_API.parseOpenAiError(500, "{\"unexpected\":true}");

        assertEquals(500, exception.getStatus());
        assertEquals("http_error", exception.getCode());
        assertEquals("OpenAI request failed (HTTP 500)", exception.getMessage());
    }

    @Test
    void handlesMalformedErrorPayloadsWithoutCrashing() {
        OpenAiException exception = OpenAIApi.OPEN_AI_API.parseOpenAiError(429, "{\"error\":{\"message\":null,\"code\":null}}") ;

        assertEquals(429, exception.getStatus());
        assertEquals("unknown_error", exception.getCode());
        assertEquals("OpenAI request failed", exception.getMessage());
    }
}
