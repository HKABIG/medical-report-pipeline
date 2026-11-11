package com.application.backend.llm;

/** The provider's daily quota is used up. Retrying today will not help, so the run stops and can resume later. */
public class QuotaExhaustedException extends LlmException {
    public QuotaExhaustedException(String message) {
        super(message);
    }
}
