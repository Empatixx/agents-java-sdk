package cz.krokviak.agents.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.AgentLoop;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunResult;
import cz.krokviak.agents.tool.MCPTool;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MCPTest {

    // -- MCPConfig tests --

    @Test
    void configDefaultsAllowAllTools() {
        MCPConfig config = MCPConfig.defaults();
        assertTrue(config.isToolAllowed("anyTool"));
        assertTrue(config.isToolAllowed("anotherTool"));
        assertNull(config.toolFilters());
        assertEquals("raise", config.errorHandling());
    }

    @Test
    void configWithFiltersRestrictsTools() {
        MCPConfig config = new MCPConfig(List.of("allowed1", "allowed2"), "raise");
        assertTrue(config.isToolAllowed("allowed1"));
        assertTrue(config.isToolAllowed("allowed2"));
        assertFalse(config.isToolAllowed("blocked"));
    }

    @Test
    void configIgnoreErrorHandling() {
        MCPConfig config = new MCPConfig(null, "ignore");
        assertEquals("ignore", config.errorHandling());
        assertTrue(config.isToolAllowed("anything"));
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

        ToolOutput result = tool.execute(Map.of("input", "hello"));
        assertInstanceOf(ToolOutput.Text.class, result);
        assertEquals("echoed: hello", ((ToolOutput.Text) result).content());
    }

    // -- MCPMessage tests --

    @Test
    void mcpMessageRequestHoldsValues() {
        MCPMessage.Request request = new MCPMessage.Request("1", "tools/list", Map.of("key", "value"));
        assertEquals("1", request.id());
        assertEquals("tools/list", request.method());
        assertEquals(Map.of("key", "value"), request.params());
    }

    @Test
    void mcpMessageResponseHoldsValues() {
        MCPMessage.Response response = new MCPMessage.Response("1", Map.of("tools", List.of()), null);
        assertEquals("1", response.id());
        assertNotNull(response.result());
        assertNull(response.error());
    }

    @Test
    void mcpMessageResponseWithError() {
        MCPMessage.MCPError error = new MCPMessage.MCPError(-32600, "Invalid Request");
        MCPMessage.Response response = new MCPMessage.Response("1", null, error);
        assertNotNull(response.error());
        assertEquals(-32600, response.error().code());
        assertEquals("Invalid Request", response.error().message());
    }

    @Test
    void mcpMessageNotificationHoldsValues() {
        MCPMessage.Notification notification = new MCPMessage.Notification("notifications/initialized", Map.of());
        assertEquals("notifications/initialized", notification.method());
        assertNotNull(notification.params());
    }

    @Test
    void mcpMessageSealedInterface() {
        // Ensure all subtypes are MCPMessage
        MCPMessage req = new MCPMessage.Request("1", "test", Map.of());
        MCPMessage resp = new MCPMessage.Response("1", null, null);
        MCPMessage notif = new MCPMessage.Notification("test", Map.of());

        assertInstanceOf(MCPMessage.class, req);
        assertInstanceOf(MCPMessage.class, resp);
        assertInstanceOf(MCPMessage.class, notif);
    }

    // -- MCPServerStdio JSON-RPC message building tests --

    @Test
    void stdioBuildsJsonRpcRequest() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcRequest(
            "42", "tools/list", Map.of("cursor", "abc"));
        assertEquals("2.0", result.get("jsonrpc"));
        assertEquals("42", result.get("id"));
        assertEquals("tools/list", result.get("method"));
        assertNotNull(result.get("params"));
    }

    @Test
    void stdioBuildsJsonRpcRequestWithNullParams() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcRequest("1", "initialize", null);
        assertEquals("2.0", result.get("jsonrpc"));
        assertEquals("1", result.get("id"));
        assertEquals("initialize", result.get("method"));
        assertFalse(result.containsKey("params"));
    }

    @Test
    void stdioBuildsJsonRpcNotification() {
        Map<String, Object> result = MCPServerStdio.buildJsonRpcNotification(
            "notifications/initialized", Map.of());
        assertEquals("2.0", result.get("jsonrpc"));
        assertEquals("notifications/initialized", result.get("method"));
        assertFalse(result.containsKey("id"));
    }

    @Test
    void stdioJsonRpcRequestSerializesToValidJson() throws Exception {
        Map<String, Object> request = MCPServerStdio.buildJsonRpcRequest(
            "1", "tools/call", Map.of("name", "echo", "arguments", Map.of("text", "hello")));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(request);

        assertNotNull(json);
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"method\":\"tools/call\""));
        assertTrue(json.contains("\"id\":\"1\""));

        // Round-trip
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(json, Map.class);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertEquals("1", parsed.get("id"));
    }

    // -- Agent builder accepts MCP fields --

    @Test
    void agentBuilderAcceptsMcpServers() {
        MockMCPServer server = new MockMCPServer(List.of(), Map.of());
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .mcpServers(List.of(server))
            .build();

        assertNotNull(agent.mcpServers());
        assertEquals(1, agent.mcpServers().size());
        assertSame(server, agent.mcpServers().get(0));
    }

    @Test
    void agentBuilderAcceptsMcpConfig() {
        MCPConfig config = new MCPConfig(List.of("tool1"), "ignore");
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .mcpConfig(config)
            .build();

        assertNotNull(agent.mcpConfig());
        assertEquals(config, agent.mcpConfig());
    }

    @Test
    void agentBuilderAddMcpServer() {
        MockMCPServer server1 = new MockMCPServer(List.of(), Map.of());
        MockMCPServer server2 = new MockMCPServer(List.of(), Map.of());
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .addMcpServer(server1)
            .addMcpServer(server2)
            .build();

        assertEquals(2, agent.mcpServers().size());
    }

    @Test
    void agentBuilderDefaultsEmptyMcpServers() {
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .build();

        assertNotNull(agent.mcpServers());
        assertTrue(agent.mcpServers().isEmpty());
        assertNull(agent.mcpConfig());
    }

    @Test
    void agentToBuilderPreservesMcpFields() {
        MCPConfig config = MCPConfig.defaults();
        MockMCPServer server = new MockMCPServer(List.of(), Map.of());
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .mcpServers(List.of(server))
            .mcpConfig(config)
            .build();

        Agent<Void> rebuilt = agent.toBuilder().build();
        assertEquals(1, rebuilt.mcpServers().size());
        assertEquals(config, rebuilt.mcpConfig());
    }

    // -- AgentLoop includes MCP tool definitions --

    @Test
    void agentLoopIncludesMCPToolDefinitions() {
        ToolDefinition toolDef = new ToolDefinition("mcp_greet", "Greet from MCP",
            Map.of("type", "object", "properties", Map.of("name",
                Map.of("type", "string", "description", "The name"))));

        MockMCPServer server = new MockMCPServer(
            List.of(toolDef),
            Map.of("mcp_greet", ToolOutput.text("Hello from MCP!"))
        );

        // Model that calls the MCP tool then returns final message
        Model toolCallingModel = new Model() {
            private int callCount = 0;
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                callCount++;
                if (callCount == 1) {
                    // Verify MCP tool is in tool definitions
                    boolean hasMcpTool = context.tools().stream()
                        .anyMatch(td -> td.name().equals("mcp_greet"));
                    if (!hasMcpTool) {
                        return new ModelResponse("r",
                            List.of(new ModelResponse.OutputItem.Message("ERROR: MCP tool not in definitions")),
                            new Usage(1, 1));
                    }
                    return new ModelResponse("r",
                        List.of(new ModelResponse.OutputItem.ToolCallRequest(
                            "call-1", "mcp_greet", Map.of("name", "World"))),
                        new Usage(10, 20));
                }
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("Done with MCP")),
                    new Usage(5, 10));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Agent<Void> agent = Agent.<Void>builder("MCPAgent")
            .instructions("Use MCP tools")
            .mcpServers(List.of(server))
            .build();

        RunResult<Void> result = AgentLoop.run(agent,
            List.of(new InputItem.UserMessage("greet")),
            new RunContext<>(null), toolCallingModel, 10);

        assertNotNull(result.finalOutput());
        assertEquals("Done with MCP", result.finalOutput());
        assertTrue(server.connectCalled);
        assertTrue(server.listToolsCalled);
        assertTrue(server.closeCalled);
    }

    @Test
    void agentLoopFiltersMCPToolsByConfig() {
        ToolDefinition allowed = new ToolDefinition("allowed_tool", "Allowed",
            Map.of("type", "object", "properties", Map.of()));
        ToolDefinition blocked = new ToolDefinition("blocked_tool", "Blocked",
            Map.of("type", "object", "properties", Map.of()));

        MockMCPServer server = new MockMCPServer(List.of(allowed, blocked), Map.of());
        MCPConfig config = new MCPConfig(List.of("allowed_tool"), "raise");

        // Model that verifies only allowed_tool is present in definitions
        Model verifyModel = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                boolean hasAllowed = context.tools().stream()
                    .anyMatch(td -> td.name().equals("allowed_tool"));
                boolean hasBlocked = context.tools().stream()
                    .anyMatch(td -> td.name().equals("blocked_tool"));
                String msg = "allowed=" + hasAllowed + " blocked=" + hasBlocked;
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message(msg)),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Agent<Void> agent = Agent.<Void>builder("FilterAgent")
            .instructions("test")
            .mcpServers(List.of(server))
            .mcpConfig(config)
            .build();

        RunResult<Void> result = AgentLoop.run(agent,
            List.of(new InputItem.UserMessage("test")),
            new RunContext<>(null), verifyModel, 10);

        assertEquals("allowed=true blocked=false", result.finalOutput());
    }

    @Test
    void agentLoopIgnoresMCPServerErrorWhenConfigured() {
        FailingMCPServer failingServer = new FailingMCPServer();
        MCPConfig config = new MCPConfig(null, "ignore");

        Model simpleModel = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("OK")),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Agent<Void> agent = Agent.<Void>builder("IgnoreAgent")
            .instructions("test")
            .mcpServers(List.of(failingServer))
            .mcpConfig(config)
            .build();

        // Should not throw despite server failure
        RunResult<Void> result = AgentLoop.run(agent,
            List.of(new InputItem.UserMessage("test")),
            new RunContext<>(null), simpleModel, 10);

        assertEquals("OK", result.finalOutput());
    }

    @Test
    void agentLoopRaisesMCPServerErrorByDefault() {
        FailingMCPServer failingServer = new FailingMCPServer();

        Agent<Void> agent = Agent.<Void>builder("RaiseAgent")
            .instructions("test")
            .mcpServers(List.of(failingServer))
            .build();

        Model dummyModel = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) { return null; }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        assertThrows(RuntimeException.class, () ->
            AgentLoop.run(agent,
                List.of(new InputItem.UserMessage("test")),
                new RunContext<>(null), dummyModel, 10));
    }

    // -- MCPException tests --

    @Test
    void mcpExceptionContainsCodeAndMessage() {
        MCPException ex = new MCPException(-32600, "Invalid Request");
        assertEquals(-32600, ex.code());
        assertTrue(ex.getMessage().contains("Invalid Request"));
        assertTrue(ex.getMessage().contains("-32600"));
    }

    // -- Helper mock classes --

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

        @Override
        public void connect() {
            connectCalled = true;
        }

        @Override
        public List<ToolDefinition> listTools() {
            listToolsCalled = true;
            return tools;
        }

        @Override
        public ToolOutput callTool(String toolName, Map<String, Object> arguments) {
            ToolOutput output = toolOutputs.get(toolName);
            return output != null ? output : ToolOutput.text("unknown tool: " + toolName);
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }

    static class FailingMCPServer implements MCPServer {
        @Override
        public void connect() throws Exception {
            throw new Exception("Connection failed");
        }

        @Override
        public List<ToolDefinition> listTools() {
            return List.of();
        }

        @Override
        public ToolOutput callTool(String toolName, Map<String, Object> arguments) {
            return ToolOutput.text("error");
        }

        @Override
        public void close() {}
    }
}
