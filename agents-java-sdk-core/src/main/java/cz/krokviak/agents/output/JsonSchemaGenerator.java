package cz.krokviak.agents.output;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.*;

public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {}

    public static Map<String, Object> generate(Class<?> type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                fillTypeInfo(prop, component.getGenericType());

                Description desc = component.getAnnotation(Description.class);
                if (desc != null) {
                    prop.put("description", desc.value());
                }

                properties.put(component.getName(), prop);
                required.add(component.getName());
            }
        }

        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static void fillTypeInfo(Map<String, Object> prop, Type type) {
        if (type instanceof Class<?> cls) {
            if (cls == String.class) {
                prop.put("type", "string");
            } else if (cls == int.class || cls == Integer.class || cls == long.class || cls == Long.class) {
                prop.put("type", "integer");
            } else if (cls == double.class || cls == Double.class || cls == float.class || cls == Float.class) {
                prop.put("type", "number");
            } else if (cls == boolean.class || cls == Boolean.class) {
                prop.put("type", "boolean");
            } else if (cls.isEnum()) {
                prop.put("type", "string");
                prop.put("enum", Arrays.stream(cls.getEnumConstants()).map(Object::toString).toList());
            } else if (cls.isRecord()) {
                prop.putAll(generate(cls));
            } else {
                prop.put("type", "object");
            }
        } else if (type instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            if (List.class.isAssignableFrom(rawType) || Collection.class.isAssignableFrom(rawType)) {
                prop.put("type", "array");
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    Map<String, Object> itemSchema = new LinkedHashMap<>();
                    fillTypeInfo(itemSchema, typeArgs[0]);
                    prop.put("items", itemSchema);
                }
            } else if (Map.class.isAssignableFrom(rawType)) {
                prop.put("type", "object");
            } else {
                prop.put("type", "object");
            }
        }
    }
}
