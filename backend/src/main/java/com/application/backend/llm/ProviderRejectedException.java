package com.application.backend.llm;

/**
 * The provider refused the request itself (an unknown model, an invalid key, a schema it cannot serve). Every other
 * document would get the same answer, so the run stops instead of sending them.
 */
public class ProviderRejectedException extends LlmException {
    public ProviderRejectedException(String message) {
        super(message);
    }
}
