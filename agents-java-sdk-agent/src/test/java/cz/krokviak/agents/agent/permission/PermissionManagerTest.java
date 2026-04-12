package cz.krokviak.agents.agent.permission;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PermissionManagerTest {

    @Test
    void trustModeAllowsEverything() {
        var pm = new PermissionManager(PermissionManager.PermissionMode.TRUST);
        assertEquals(PermissionManager.PermissionResult.ALLOW,
            pm.check("bash", Map.of("command", "rm -rf /")));
    }

    @Test
    void denyAllModeDeniesPermissionRequired() {
        var pm = new PermissionManager(PermissionManager.PermissionMode.DENY_ALL);
        assertEquals(PermissionManager.PermissionResult.DENY,
            pm.check("bash", Map.of("command", "ls")));
    }

    @Test
    void defaultModeAutoAllowsReadTools() {
        var pm = new PermissionManager(PermissionManager.PermissionMode.DEFAULT);
        assertEquals(PermissionManager.PermissionResult.ALLOW,
            pm.check("read_file", Map.of()));
        assertEquals(PermissionManager.PermissionResult.ALLOW,
            pm.check("glob", Map.of()));
        assertEquals(PermissionManager.PermissionResult.ALLOW,
            pm.check("grep", Map.of()));
    }

    @Test
    void defaultModeAutoAllowsAgentTool() {
        var pm = new PermissionManager(PermissionManager.PermissionMode.DEFAULT);
        assertEquals(PermissionManager.PermissionResult.ALLOW,
            pm.check("agent", Map.of()));
    }

    @Test
    void sessionRulesEmpty() {
        var pm = new PermissionManager(PermissionManager.PermissionMode.DEFAULT);
        assertTrue(pm.sessionRules().isEmpty());
    }
}
