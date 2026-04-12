package cz.krokviak.agents.agent.hook.builtin;

import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import cz.krokviak.agents.agent.hook.*;
import cz.krokviak.agents.agent.permission.PermissionManager;

public class PermissionHook implements Hook {
    private final PermissionManager permissionManager;

    public PermissionHook(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public HookPhase phase() { return HookPhase.PRE_TOOL; }

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (permissionManager == null) return new HookResult.Proceed();
        var result = permissionManager.check(event.toolName(), event.args());
        if (result == PermissionManager.PermissionResult.DENY) {
            return new HookResult.Block("Permission denied by user");
        }
        return new HookResult.Proceed();
    }
}
