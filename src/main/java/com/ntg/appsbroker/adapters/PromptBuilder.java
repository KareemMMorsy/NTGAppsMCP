package com.ntg.appsbroker.adapters;

import java.util.Map;

/**
 * Adapter: Builds provider-neutral prompts for AI.
 */
public class PromptBuilder {
    
    public String buildIntentPrompt(String userMessage, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant that helps users interact with the NTG Apps Broker system.\n");
        prompt.append("Analyze the following user message and determine the user's intent.\n\n");
        
        if (context != null && !context.isEmpty()) {
            prompt.append("Context:\n");
            context.forEach((key, value) -> 
                prompt.append(String.format("- %s: %s\n", key, value))
            );
            prompt.append("\n");
        }
        
        prompt.append("User message: ").append(userMessage).append("\n\n");
        prompt.append("Respond with a JSON object containing the intent and any extracted parameters.");
        
        return prompt.toString();
    }
}

