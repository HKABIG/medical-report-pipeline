package com.application.backend.llm;

/**
 * @param text  the model's JSON answer as text
 * @param model provider and model, written as provider/model
 */
public record LlmResponse(String text, String model) {
}
