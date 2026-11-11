package com.application.backend.llm;

/** Answers from recordings only. A request with no successful recording is an error, never a network call. */
public class ReplayLlmClient implements LlmClient {

    private final FixtureStore fixtures;

    public ReplayLlmClient(FixtureStore fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Fixture fixture = fixtures.load(request.correlationId())
                .orElseThrow(() -> new LlmException("No recording for " + request.correlationId()));
        String text = fixture.answer()
                .orElseThrow(() -> new LlmException("Recording for " + request.correlationId()
                        + " holds no successful answer"));
        return new LlmResponse(text, fixture.qualifiedModel());
    }
}
