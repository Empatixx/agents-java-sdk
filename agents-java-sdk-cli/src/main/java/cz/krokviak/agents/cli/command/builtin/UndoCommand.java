package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.runner.InputItem;
public class UndoCommand implements Command {
    @Override public String name() { return "undo"; }
    @Override public String description() { return "Undo last turn"; }
    @Override public void execute(String args, CliContext ctx) {
        var history = ctx.history();
        if (history.size() < 2) { ctx.output().printError("Nothing to undo."); return; }
        while (!history.isEmpty() && !(history.getLast() instanceof InputItem.UserMessage)) { history.removeLast(); }
        if (!history.isEmpty()) history.removeLast();
        ctx.output().println("Last turn undone.");
    }
}
