package cz.krokviak.agents;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hard guardrails on the module dependency graph. Rules here are the
 * architecture contract — they fail loud at CI time before a bad import
 * can merge.
 *
 * <p>Lives in {@code -cli} because it's the only module that has all
 * other modules on its compile classpath (transitively). Scans all
 * {@code cz.krokviak.agents.*} classes on that classpath.
 */
class ArchitectureBoundariesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("cz.krokviak.agents");

    @Test
    void coreMustNotDependOnAnyDownstreamModule() {
        noClasses()
            .that().resideInAPackage("cz.krokviak.agents..")
                .and().resideOutsideOfPackages(
                    "cz.krokviak.agents.cli..",
                    "cz.krokviak.agents.agent..",
                    "cz.krokviak.agents.api..",
                    "cz.krokviak.agents.mcp..",
                    "cz.krokviak.agents.session..",
                    "cz.krokviak.agents.util..")
                // The above selects -core (non-namespaced packages: runner, model, tool, context, handoff, ...).
            .should().dependOnClassesThat().resideInAnyPackage(
                "cz.krokviak.agents.cli..",
                "cz.krokviak.agents.agent..",
                "cz.krokviak.agents.mcp..")
            .check(CLASSES);
    }

    @Test
    void agentApiMustOnlyDependOnCore() {
        noClasses()
            .that().resideInAPackage("cz.krokviak.agents.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "cz.krokviak.agents.cli..",
                "cz.krokviak.agents.agent..",
                "cz.krokviak.agents.mcp..")
            .check(CLASSES);
    }

    @Test
    void agentModuleMustNotDependOnCli() {
        noClasses()
            .that().resideInAPackage("cz.krokviak.agents.agent..")
            .should().dependOnClassesThat().resideInAPackage("cz.krokviak.agents.cli..")
            .check(CLASSES);
    }

    @Test
    void agentApiDoesNotImportTuiLibraries() {
        noClasses()
            .that().resideInAPackage("cz.krokviak.agents.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.tamboui..",
                "org.jline..")
            .check(CLASSES);
    }

    @Test
    void coreDoesNotImportTuiLibraries() {
        // Exception: adapter modules (Anthropic/OpenAI) live in core namespace but
        // currently do not import TUI; locking this in prevents future drift.
        noClasses()
            .that().resideInAnyPackage(
                "cz.krokviak.agents.runner..",
                "cz.krokviak.agents.model..",
                "cz.krokviak.agents.tool..",
                "cz.krokviak.agents.session..",
                "cz.krokviak.agents.tracing..",
                "cz.krokviak.agents.handoff..",
                "cz.krokviak.agents.context..",
                "cz.krokviak.agents.guardrail..",
                "cz.krokviak.agents.hook..",
                "cz.krokviak.agents.output..",
                "cz.krokviak.agents.http..",
                "cz.krokviak.agents.streaming..",
                "cz.krokviak.agents.exception..",
                "cz.krokviak.agents.util..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.tamboui..",
                "org.jline..")
            .check(CLASSES);
    }

}
