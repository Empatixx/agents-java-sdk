package cz.krokviak.agents.cli.task;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskManager {
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final List<TaskNotification> pendingNotifications = Collections.synchronizedList(new ArrayList<>());

    public record TaskNotification(String taskId, String description, TaskState.Status status, String summary) {}

    public String nextId() { return "task-" + counter.incrementAndGet(); }
    public void register(TaskState task) { tasks.put(task.id(), task); }
    public TaskState get(String id) { return tasks.get(id); }
    public List<TaskState> all() {
        return tasks.values().stream()
            .sorted(Comparator.comparing(TaskState::id))
            .toList();
    }
    public List<TaskState> running() { return tasks.values().stream().filter(t -> t.status() == TaskState.Status.RUNNING).toList(); }

    public void addNotification(TaskNotification n) { pendingNotifications.add(n); }
    public List<TaskNotification> drainNotifications() {
        if (pendingNotifications.isEmpty()) return List.of();
        var copy = List.copyOf(pendingNotifications); pendingNotifications.clear(); return copy;
    }

    public void killTask(String id) {
        TaskState task = tasks.get(id);
        if (task != null && (task.status() == TaskState.Status.RUNNING || task.status() == TaskState.Status.PENDING)) {
            task.kill();
            addNotification(new TaskNotification(id, task.description(), TaskState.Status.KILLED, "Killed by user"));
        }
    }
}
