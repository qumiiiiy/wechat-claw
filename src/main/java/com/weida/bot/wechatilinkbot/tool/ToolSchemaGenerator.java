package com.weida.bot.wechatilinkbot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Collection;

/**
 * Builds the small JSON Schema subset required by project tools.
 */
final class ToolSchemaGenerator {

    private ToolSchemaGenerator() {
    }

    static String generate(Class<?> inputType, ObjectMapper objectMapper) {
        if (!inputType.isRecord()) {
            throw new IllegalArgumentException("工具输入类型必须是 record");
        }
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        for (RecordComponent component : inputType.getRecordComponents()) {
            ToolParameter parameter = component.getAnnotation(ToolParameter.class);
            ObjectNode property = properties.putObject(component.getName());
            appendType(property, component);
            if (parameter != null && !parameter.description().isBlank()) {
                property.put("description", parameter.description().strip());
            }
            if (parameter == null || parameter.required()) {
                required.add(component.getName());
            }
        }
        schema.put("additionalProperties", false);
        return schema.toString();
    }

    private static void appendType(ObjectNode property, RecordComponent component) {
        Class<?> type = component.getType();
        if (Collection.class.isAssignableFrom(type)) {
            property.put("type", "array");
            ObjectNode items = property.putObject("items");
            items.put("type", collectionItemType(component));
            return;
        }
        if (type == boolean.class || type == Boolean.class) {
            property.put("type", "boolean");
        } else if (type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || Number.class.isAssignableFrom(type)) {
            property.put("type", "integer");
        } else {
            property.put("type", "string");
        }
    }

    private static String collectionItemType(RecordComponent component) {
        if (component.getGenericType() instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] == String.class) {
            return "string";
        }
        return "object";
    }
}
