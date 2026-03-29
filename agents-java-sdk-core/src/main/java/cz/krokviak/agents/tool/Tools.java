package cz.krokviak.agents.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Tools {

    private Tools() {}

    public static List<Tool> fromClass(Object instance) {
        List<Tool> tools = new ArrayList<>();
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(FunctionTool.class)) {
                tools.add(FunctionToolImpl.fromMethod(instance, method));
            }
        }
        return tools;
    }

    public static FunctionToolBuilder function(String name) {
        return new FunctionToolBuilder(name);
    }
}
