package cz.krokviak.agents.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.AgentLoop;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunResult;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MCPTest {

    // -- MCPConfig tests --

    @Test
    void configDefaultsAllowAllTools() {
        MCPConfig config = MCPConfig.defaults();
        assertTrue(config.isToolAllowed("anyTool"));
        assertNull(config.toolFilters());
        assertEquals("raise", config.errorHandling());
    }

    @Test
    void configWithFiltersRestrictsTools() {
        MCPConfig config = new MCPConfig(List.of("allowed1", "allowed2"), "raise");
        assertTrue(config.isToolAllowed("allowed1"));
        assertFalse(config.isToolAllowed("blocked"));
    }

    @Test
    void configIgnoreErrorHandling() {
        MCPConfig config = new MCPConfig(null, "ignore");
        assertEquals("ignore", config.errorHandling());
    }

    // -- MCPTool tests --

    @Test
    void mcpToolWrapsDefinitionCorrectly() {
        ToolDefinition def = new ToolDefinition("my_tool", "A test tool",
            Map.of("type", "object", "properties", Map.of()));
        MCPServer mockServer = new MockMCPServer(List.of(def), Map.of());
        MCPTool tool = new MCPTool(def, mockServer);

        assertEquals("my_tool", tool.name());
        assertEquals("A test tool", tool.description());
        assertEquals(def, tool.definition());
        assertSame(mockServer, tool.server());
    }

    @Test
    void mcpToolExecutesDelegatesToServer() throws Exception {
        ToolDefinition def = new ToolDefinition("echo", "Echo tool",
            Map.of("type", "object", "properties", Map.of()));
        MockMCPServer server = new MockMCPServer(List.of(def),
            Map.of("echo", ToolOutput.text("echoed: hello")));
        MCPTool tool = new MCPTool(def, server);

        ToolOutput result = tool.execute(new ToolArgs(Map.of("input", "hello")), null);
        assertInstanceOf(ToolOutput.Text.class, result);
        assertEquals("echoed: hello", ((ToolOutput.Text) result).content());
    }

    // -- MCPMessage tests --

    @Test
    void mcpMessageRequestHoldsValues() {
        MCPMessage.Request request = new MCPMessage.Request("1", "tools/list", Map.of("key", "value"));
        assertEquals("1", request.id());
        assertEquals("tools/list", request.method());
    }

    @Test
    void mcpMessageResponseHoldsValues() {
        MCPMessage.Response response = new MCPMessage.Response("1", Map.of("tools", List.of()), null);
        assertNotNull(response.result());
        assertNull(response.error());
    }

    @Test
    void mcpMessageResponseWithError() {
        MCPMessage.MCPError error = new MCPMessage.MCPError(-32600, "Invalid Request");
        MCPMessage.Response response = new MCPMessage.Response("1", null, error);
        assertNotNull(response.error());
        assertEquals(-32600, response.error().code());
    }

    @Test
    void mcpMessageNotificationHoldsValues() {
        MCPMessage.Notification notification = new MCPMessage.Notification("notifications/initialized", Map.of());
        assertEquals("notifications/initialized", notification.method());
    }

    @Test
    void mcpMessageSealedInterface() {
        assertInstanceOf(MCPMessage.class, new MCPMessage.Request("1", "test", Map.of()));
        assertInstanceOf(MCPMessage.class, new MCPMessage.Response("1", null, null));
        assertInstanceOf(MCPMessage.class, new MCPMessage.Notification("test", Map.of()));
    }

    // -- MCPServerStdio JSON-RPC tests --

    @Test
    void stdioBuildsJsonRpcRequest() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcRequest("42", "tools/list", Map.of("cursor", "abc"));
        assertEquals("2.0", result.get("jsonrpc"));
        assertEquals("42", result.get("id"));
        assertEquals("tools/list", result.get("method"));
    }

    @Test
    void stdioBuildsJsonRpcRequestWithNullParams() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcRequest("1", "initialize", null);
        assertFalse(result.containsKey("params"));
    }

    @Test
    void stdioBuildsJsonRpcNotification() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcNotification("notifications/initialized", Map.of());
        assertEquals("2.0", result.get("jsonrpc"));
        assertFalse(result.containsKey("id"));
    }

    @Test
    void stdioJsonRpcRequestSerializesToValidJson() throws Exception {
        Map<String, Object> request = MCPServerStdio.buildJsonRpcRequest("1", "tools/call", Map.of("name", "echo"));
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
    }

    // -- MCPToolProvider tests --

    @Test
    void toolProviderResolvesTools() throws Exception {
        ToolDefinition def = new ToolDefinition("tool1", "Tool 1", Map.of("type", "object", "properties", Map.of()));
        MockMCPServer server = new MockMCPServer(List.of(def), Map.of());
        MCPToolProvider provider = new MCPToolProvider(server);

        var tools = provider.provideTools();
        assertEquals(1, tools.size());
        assertEquals("tool1", tools.getFirst().name());
        assertTrue(server.connectCalled);
    }

    @Test
    void toolProviderFiltersTools() throws Exception {
        ToolDefinition allowed = new ToolDefinition("allowed", "A", Map.of("type", "object", "properties", Map.of()));
        ToolDefinition blocked = new ToolDefinition("blocked", "B", Map.of("type", "object", "properties", Map.of()));
        MockMCPServer server = new MockMCPServer(List.of(allowed, blocked), Map.of());
        MCPToolProvider provider = new MCPToolProvider(List.of(server), new MCPConfig(List.of("allowed"), "raise"));

        var tools = provider.provideTools();
        assertEquals(1, tools.size());
        assertEquals("allowed", tools.getFirst().name());
    }

    @Test
    void toolProviderClosesServers() {
        MockMCPServer server = new MockMCPServer(List.of(), Map.of());
        MCPToolProvider provider = new MCPToolProvider(server);
        provider.close();
        assertTrue(server.closeCalled);
    }

    // -- Agent builder with ToolProvider --

    @Test
    void agentBuilderAcceptsToolProvider() {
        MockMCPServer server = new MockMCPServer(List.of(), Map.of());
        MCPToolProvider provider = new MCPToolProvider(server);
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .addToolProvider(provider)
            .build();

        assertEquals(1, agent.toolProviders().size());
    }

    @Test
    void agentBuilderDefaultsEmptyToolProviders() {
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .build();

        assertNotNull(agent.toolProviders());
        assertTrue(agent.toolProviders().isEmpty());
    }

    // -- AgentLoop with ToolProvider --

    @Test
    void agentLoopIncludesProviderToolDefinitions() {
        ToolDefinition toolDef = new ToolDefinition("mcp_greet", "Greet from MCP",
            Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));
        MockMCPServer server = new MockMCPServer(List.of(toolDef),
            Map.of("mcp_greet", ToolOutput.text("Hello from MCP!")));

        Model toolCallingModel = new Model() {
            private int callCount = 0;
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                callCount++;
                if (callCount == 1) {
                    boolean hasMcpTool = context.tools().stream().anyMatch(td -> td.name().equals("mcp_greet"));
                    if (!hasMcpTool) {
                        return new ModelResponse("r", List.of(new ModelResponse.OutputItem.Message("ERROR: MCP tool not found")), new Usage(1, 1));
                    }
                    return new ModelResponse("r", List.of(new ModelResponse.OutputItem.ToolCallRequest("call-1", "mcp_greet", Map.of("name", "World"))), new Usage(10, 20));
                }
                return new ModelResponse("r", List.of(new ModelResponse.OutputItem.Message("Done with MCP")), new Usage(5, 10));
            }
            @Override public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Agent<Void> agent = Agent.<Void>builder("MCPAgent")
            .instructions("Use MCP tools")
            .addToolProvider(new MCPToolProvider(server))
            .build();

        RunResult<Void> result = AgentLoop.run(agent, List.of(new InputItem.UserMessage("greet")), new RunContext<>(null), toolCallingModel, 10);
        assertEquals("Done with MCP", result.finalOutput());
        assertTrue(server.connectCalled);
        assertTrue(server.listToolsCalled);
    }

    @Test
    void agentLoopIgnoresFailingProvider() {
        FailingMCPServer failingServer = new FailingMCPServer();
        MCPToolProvider provider = new MCPToolProvider(List.of(failingServer), new MCPConfig(null, "ignore"));

        Model simpleModel = new Model() {
            @Override public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r", List.of(new ModelResponse.OutputItem.Message("OK")), new Usage(1, 1));
            }
            @Override public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Agent<Void> agent = Agent.<Void>builder("Agent")
            .instructions("test")
            .addToolProvider(provider)
            .build();

        RunResult<Void> result = AgentLoop.run(agent, List.of(new InputItem.UserMessage("test")), new RunContext<>(null), simpleModel, 10);
        assertEquals("OK", result.finalOutput());
    }

    // -- MCPException --

    @Test
    void mcpExceptionContainsCodeAndMessage() {
        MCPException ex = new MCPException(-32600, "Invalid Request");
        assertEquals(-32600, ex.code());
        assertTrue(ex.getMessage().contains("Invalid Request"));
    }

    // -- Mock helpers --

    static class MockMCPServer implements MCPServer {
        final List<ToolDefinition> tools;
        final Map<String, ToolOutput> toolOutputs;
        boolean connectCalled = false;
        boolean listToolsCalled = false;
        boolean closeCalled = false;

        MockMCPServer(List<ToolDefinition> tools, Map<String, ToolOutput> toolOutputs) {
            this.tools = tools;
            this.toolOutputs = toolOutputs;
        }

        @Override public void connect() { connectCalled = true; }
        @Override public List<ToolDefinition> listTools() { listToolsCalled = true; return tools; }
        @Override public ToolOutput callTool(String toolName, Map<String, Object> arguments) {
            ToolOutput output = toolOutputs.get(toolName);
            return output != null ? output : ToolOutput.text("unknown tool: " + toolName);
        }
        @Override public void close() { closeCalled = true; }
    }

    static class FailingMCPServer implements MCPServer {
        @Override public void connect() throws Exception { throw new Exception("Connection failed"); }
        @Override public List<ToolDefinition> listTools() { return List.of(); }
        @Override public ToolOutput callTool(String toolName, Map<String, Object> arguments) { return ToolOutput.text("error"); }
        @Override public void close() {}
    }
}
