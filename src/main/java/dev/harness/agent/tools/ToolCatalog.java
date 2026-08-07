package dev.harness.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.harness.agent.tools.GameRecommendationTools.SUMMARIZER;

@Component
public class ToolCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolCallbackProvider callbackProvider;

    public ToolCatalog(GameRecommendationTools tools) {
        this.callbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }

    public ToolCallbackProvider callbackProvider() {
        return callbackProvider;
    }

    public List<ToolDefinitionView> definitions() {
        return Arrays.stream(callbackProvider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(definition -> new ToolDefinitionView(
                        definition.name(),
                        definition.description(),
                        definition.inputSchema(),
                        roleOf(definition.name())))
                .toList();
    }

    public Set<String> toolNames() {
        return definitions().stream()
                .map(ToolDefinitionView::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasTool(String name) {
        return toolNames().contains(name);
    }

    public Set<String> argumentNames(String name) {
        return definitionFor(name)
                .map(ToolDefinitionView::inputSchema)
                .map(this::argumentNamesFromSchema)
                .orElse(Set.of());
    }

    public Set<String> requiredArgumentNames(String name) {
        return definitionFor(name)
                .map(ToolDefinitionView::inputSchema)
                .map(this::requiredArgumentNamesFromSchema)
                .orElse(Set.of());
    }

    public ToolRole roleOf(String name) {
        return SUMMARIZER.equals(name) ? ToolRole.FINAL_SYNTHESIS : ToolRole.DATA;
    }

    public List<ToolDefinitionView> finalSynthesisTools() {
        return definitions().stream()
                .filter(definition -> definition.role() == ToolRole.FINAL_SYNTHESIS)
                .toList();
    }

    public Optional<ToolDefinitionView> finalSynthesisTool() {
        List<ToolDefinitionView> tools = finalSynthesisTools();
        return tools.size() == 1 ? Optional.of(tools.getFirst()) : Optional.empty();
    }

    private Optional<ToolDefinitionView> definitionFor(String name) {
        return definitions().stream()
                .filter(definition -> definition.name().equals(name))
                .findFirst();
    }

    private Set<String> argumentNamesFromSchema(String schema) {
        JsonNode properties = parseSchema(schema).path("properties");
        if (!properties.isObject()) {
            return Set.of();
        }

        return java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(properties.fieldNames(), 0), false)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> requiredArgumentNamesFromSchema(String schema) {
        JsonNode required = parseSchema(schema).path("required");
        if (!required.isArray()) {
            return Set.of();
        }

        return java.util.stream.StreamSupport.stream(required.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private JsonNode parseSchema(String schema) {
        try {
            return OBJECT_MAPPER.readTree(schema == null || schema.isBlank() ? "{}" : schema);
        } catch (Exception exception) {
            throw new IllegalStateException("invalid tool input schema", exception);
        }
    }
}
