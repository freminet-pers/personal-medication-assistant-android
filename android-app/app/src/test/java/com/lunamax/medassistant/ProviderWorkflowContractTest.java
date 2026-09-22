package com.lunamax.medassistant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for the v0.4.0 Provider and independent-search workflow. */
public final class ProviderWorkflowContractTest {
    @Test public void builtInDeepSeekUsesOneTextAndImageModel() {
        ProviderProfile profile = ProviderProfile.builtInDeepSeek();
        assertEquals("DeepSeek V4.1 Flash", profile.displayName);
        assertEquals("deepseek-flash", profile.modelId);
        assertEquals(ProviderProfile.OPENAI_CHAT_COMPLETIONS, profile.protocol);
        assertTrue(profile.supportsText);
        assertTrue(profile.supportsImage);
        assertTrue(profile.imageInputEnabled);
    }

    @Test public void endpointResolverRemovesKnownEndpointSuffixes() {
        ProviderProfile profile = ProviderProfile.builder()
                .protocol(ProviderProfile.OPENAI_RESPONSES)
                .baseUrl("https://api.example.test/v1/responses")
                .modelId("contract-model")
                .build();
        assertEquals("https://api.example.test/v1/responses", EndpointResolver.endpoint(profile));
        assertEquals("https://api.example.test/v1/responses", EndpointResolver.endpoint(profile.toBuilder().baseUrl("https://api.example.test/v1").build()));
        assertEquals("https://api.example.test/v1/messages", EndpointResolver.searchEndpoint("https://api.example.test/v1", SearchProfile.ANTHROPIC_NATIVE));
    }

    @Test public void loopbackHttpIsAllowedButPublicHttpIsRejected() {
        assertEquals("http://127.0.0.1:8080/v1", EndpointResolver.normalizeBaseUrl("http://127.0.0.1:8080/v1/"));
        boolean rejected = false;
        try {
            EndpointResolver.normalizeBaseUrl("http://api.example.test/v1");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test public void searchProfileClampsMaxUsesAndStartsDisabled() {
        SearchProfile profile = SearchProfile.builder().maxUses(99).build();
        assertEquals(10, profile.maxUses);
        assertFalse(profile.enabled);
        assertEquals("NOT_TESTED", profile.testState);
    }
}
