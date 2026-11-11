package com.application.backend.llm;

/** The language model as the pipeline sees it. Implementations decide whether to call a provider or replay. */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);
}
