package cz.krokviak.agents.output;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaGeneratorTest {

    public record SimpleEvent(
        @Description("Name of the event") String name,
        @Description("ISO date string") String date
    ) {}

    public record WithList(
        @Description("Tags") List<String> tags,
        @Description("Count") int count
    ) {}

    public enum Priority { LOW, MEDIUM, HIGH }

    public record WithEnum(
        @Description("Priority level") Priority priority,
        @Description("Label") String label
    ) {}

    @Test
    void generatesSchemaForSimpleRecord() {
        var schema = JsonSchemaGenerator.generate(SimpleEvent.class);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("date"));

        @SuppressWarnings("unchecked")
        var nameProp = (Map<String, Object>) props.get("name");
        assertEquals("string", nameProp.get("type"));
        assertEquals("Name of the event", nameProp.get("description"));
    }

    @Test
    void generatesSchemaWithListType() {
        var schema = JsonSchemaGenerator.generate(WithList.class);

        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) schema.get("properties");

        @SuppressWarnings("unchecked")
        var tagsProp = (Map<String, Object>) props.get("tags");
        assertEquals("array", tagsProp.get("type"));

        @SuppressWarnings("unchecked")
        var countProp = (Map<String, Object>) props.get("count");
        assertEquals("integer", countProp.get("type"));
    }

    @Test
    void generatesSchemaWithEnum() {
        var schema = JsonSchemaGenerator.generate(WithEnum.class);

        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) schema.get("properties");

        @SuppressWarnings("unchecked")
        var priorityProp = (Map<String, Object>) props.get("priority");
        assertEquals("string", priorityProp.get("type"));
        assertNotNull(priorityProp.get("enum"));
    }

    @Test
    void requiredFieldsIncludesAll() {
        var schema = JsonSchemaGenerator.generate(SimpleEvent.class);

        @SuppressWarnings("unchecked")
        var required = (List<String>) schema.get("required");
        assertTrue(required.contains("name"));
        assertTrue(required.contains("date"));
    }
}
