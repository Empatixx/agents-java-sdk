package cz.krokviak.agents.cli.hook.builtin;

import cz.krokviak.agents.cli.hook.*;
import cz.krokviak.agents.cli.permission.PermissionManager;

public class PermissionHook implements Hook {
    private final PermissionManager permissionManager;

    public PermissionHook(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public Phase phase() { return Phase.PRE_TOOL; }

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
