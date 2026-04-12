package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class UndoCommand implements Command {
    @Override public String name() { return "undo"; }
    @Override public String description() { return "Undo last turn"; }
    @Override public void execute(String args, CliContext ctx) {
        if (ctx.agent().history().size() < 2) { ctx.output().printError("Nothing to undo."); return; }
        ctx.agent().undoLastTurn();
        ctx.output().println("Last turn undone.");
    }
}
