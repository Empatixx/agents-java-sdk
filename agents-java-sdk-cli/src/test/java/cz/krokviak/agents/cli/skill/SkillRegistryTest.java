package cz.krokviak.agents.cli.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    @Test
    void registerAndGet() {
        var reg = new SkillRegistry();
        var skill = new Skill("commit", "commit changes", "Run git commit", Map.of(), "test");
        reg.register(skill);

        assertTrue(reg.get("commit").isPresent());
        assertEquals("commit changes", reg.get("commit").get().description());
    }

    @Test
    void getMissing() {
        var reg = new SkillRegistry();
        assertTrue(reg.get("nonexistent").isEmpty());
    }

    @Test
    void listAll() {
        var reg = new SkillRegistry();
        reg.register(new Skill("a", "desc a", "content", Map.of(), ""));
        reg.register(new Skill("b", "desc b", "content", Map.of(), ""));
        assertEquals(2, reg.list().size());
    }

    @Test
    void searchByName() {
        var reg = new SkillRegistry();
        reg.register(new Skill("code-review", "review code", "content", Map.of(), ""));
        reg.register(new Skill("deploy", "deploy app", "content", Map.of(), ""));

        var results = reg.search("review");
        assertFalse(results.isEmpty());
        assertEquals("code-review", results.getFirst().name());
    }

    @Test
    void searchByDescription() {
        var reg = new SkillRegistry();
        reg.register(new Skill("lint", "run the linter on code", "content", Map.of(), ""));

        var results = reg.search("linter");
        assertFalse(results.isEmpty());
    }

    @Test
    void searchNoMatch() {
        var reg = new SkillRegistry();
        reg.register(new Skill("deploy", "deploy", "content", Map.of(), ""));
        assertTrue(reg.search("zzzzz").isEmpty());
    }

    @Test
    void laterRegistrationOverrides() {
        var reg = new SkillRegistry();
        reg.register(new Skill("x", "old", "old content", Map.of(), ""));
        reg.register(new Skill("x", "new", "new content", Map.of(), ""));

        assertEquals("new", reg.get("x").get().description());
        assertEquals(1, reg.list().size());
    }

    @Test
    void isUserInvocable() {
        var invocable = new Skill("a", "", "", Map.of("user_invocable", "true"), "");
        var notInvocable = new Skill("b", "", "", Map.of(), "");

        assertTrue(invocable.isUserInvocable());
        assertFalse(notInvocable.isUserInvocable());
    }
}
