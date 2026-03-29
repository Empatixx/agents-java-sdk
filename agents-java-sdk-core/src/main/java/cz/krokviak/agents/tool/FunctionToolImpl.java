package cz.krokviak.agents.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.exception.ToolExecutionException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.BiFunction;

public final class FunctionToolImpl implements Tool {
    private final String name;
    private final String description;
    private final List<ParamInfo> params;
    private final BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler;
    private final ToolDefinition toolDefinition;
    private final java.util.function.Predicate<Void> enabledPredicate;
    private final BiFunction<String, Exception, ToolOutput> failureErrorFunction;

    record ParamInfo(String name, String description, Class<?> type) {}

    FunctionToolImpl(String name, String description, List<ParamInfo> params,
                     BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler) {
        this(name, description, params, handler, null, null);
    }

    FunctionToolImpl(String name, String description, List<ParamInfo> params,
                     BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler,
                     java.util.function.Predicate<Void> enabledPredicate,
                     BiFunction<String, Exception, ToolOutput> failureErrorFunction) {
        this.name = name;
        this.description = description;
        this.params = params;
        this.handler = handler;
        this.enabledPredicate = enabledPredicate;
        this.failureErrorFunction = failureErrorFunction;
        this.toolDefinition = buildDefinition();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean isEnabled() {
        return enabledPredicate == null || enabledPredicate.test(null);
    }

    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        try {
            return handler.apply(args, ctx);
        } catch (Exception e) {
            if (failureErrorFunction != null) {
                return failureErrorFunction.apply(name, e);
            }
            throw new ToolExecutionException(name, e);
        }
    }

    public ToolDefinition definition() {
        return toolDefinition;
    }

    public List<ParamInfo> params() {
        return params;
    }

    private ToolDefinition buildDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParamInfo param : params) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(param.type()));
            prop.put("description", param.description());
            properties.put(param.name(), prop);
            required.add(param.name());
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);

        return new ToolDefinition(name, description, schema);
    }

    private String jsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }

    static FunctionToolImpl fromMethod(Object instance, Method method) {
        FunctionTool annotation = method.getAnnotation(FunctionTool.class);
        String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
        String toolDescription = annotation.description();

        Parameter[] methodParams = method.getParameters();
        List<ParamInfo> paramInfos = new ArrayList<>();
        boolean hasToolContext = false;
        int contextParamIndex = -1;

        for (int i = 0; i < methodParams.length; i++) {
            if (ToolContext.class.isAssignableFrom(methodParams[i].getType())) {
                hasToolContext = true;
                contextParamIndex = i;
                continue;
            }
            Param paramAnnotation = methodParams[i].getAnnotation(Param.class);
            String paramName = methodParams[i].getName();
            String paramDesc = paramAnnotation != null ? paramAnnotation.value() : paramName;
            paramInfos.add(new ParamInfo(paramName, paramDesc, methodParams[i].getType()));
        }

        final int ctxIdx = contextParamIndex;
        final List<ParamInfo> finalParams = List.copyOf(paramInfos);

        BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler = (args, ctx) -> {
            try {
                Object[] invokeArgs = new Object[methodParams.length];
                int paramIdx = 0;
                for (int i = 0; i < methodParams.length; i++) {
                    if (i == ctxIdx) {
                        invokeArgs[i] = ctx;
                    } else {
                        ParamInfo pi = finalParams.get(paramIdx++);
                        Object value = args.raw().get(pi.name());
                        invokeArgs[i] = convertValue(value, methodParams[i].getType());
                    }
                }
                method.setAccessible(true);
                return (ToolOutput) method.invoke(instance, invokeArgs);
            } catch (Exception e) {
                throw new ToolExecutionException(toolName, e);
            }
        };

        return new FunctionToolImpl(toolName, toolDescription, finalParams, handler, null, null);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        String str = value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        return str;
    }
}
