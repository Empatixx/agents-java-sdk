package cz.krokviak.agents.api.dto;

/**
 * Snapshot of a task tracked by the agent. Listed via
 * {@link cz.krokviak.agents.api.AgentService#listTasks()}; inspected via
 * {@link cz.krokviak.agents.api.AgentService#getTask(String)}.
 *
 * @param id           task id (e.g. {@code "task-1"})
 * @param description  free-form short description
 * @param status       lifecycle: {@code PENDING}, {@code RUNNING}, {@code COMPLETED}, {@code FAILED}, {@code KILLED}
 * @param summary      result / error summary once terminal; may be {@code null} while running
 * @param startedAt    epoch-ms when the task entered RUNNING; {@code 0} if never started
 * @param finishedAt   epoch-ms when the task terminated; {@code 0} while still running
 */
public record TaskInfo(
    String id,
    String description,
    String status,
    String summary,
    long startedAt,
    long finishedAt
) {}
