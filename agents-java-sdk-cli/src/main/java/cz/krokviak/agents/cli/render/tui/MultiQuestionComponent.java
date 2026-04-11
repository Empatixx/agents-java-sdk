package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Multi-question prompt with left/right navigation between questions
 * and up/down navigation between numbered options within each question.
 */
public final class MultiQuestionComponent {
    private MultiQuestionComponent() {}

    public static Element render(CliController ctrl, Runnable onAllDone) {
        var questions = ctrl.multiQuestions();
        int activeIdx = ctrl.activeQuestionIdx();
        var active = questions.get(activeIdx);
        String[] confirmed = ctrl.confirmedAnswers();

        // Tab bar: header tags
        List<Element> tabElements = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i);
            String tag = q.header() != null ? q.header() : "Q" + (i + 1);
            boolean done = confirmed[i] != null;
            if (i == activeIdx) {
                tabElements.add(text(" " + tag + " ").bold().yellow().fit());
            } else if (done) {
                tabElements.add(text(" " + tag + " \u2713 ").green().fit());
            } else {
                tabElements.add(text(" " + tag + " ").dim().fit());
            }
        }
        Element tabBar = row(tabElements.toArray(Element[]::new));

        // Numbered options with ListElement
        int selectedIdx = ctrl.selectedOptionForActive();
        String[] numbered = new String[active.options().length];
        for (int i = 0; i < active.options().length; i++) {
            numbered[i] = (i + 1) + ". " + active.options()[i];
        }

        var optionList = list()
            .items(numbered)
            .highlightColor(Color.YELLOW)
            .highlightSymbol("\u276f ")
            .selected(selectedIdx);

        // Navigation hint
        Element navHint;
        if (questions.size() > 1) {
            navHint = text("  \u25c0 " + (activeIdx + 1) + "/" + questions.size() + " \u25b6  Enter to confirm").dim();
        } else {
            navHint = text("  Enter to confirm").dim();
        }

        // Build content column
        List<Element> content = new ArrayList<>();
        content.add(tabBar);
        content.add(text(""));
        content.add(text(active.text()).bold());
        content.add(text(""));
        content.add(optionList
            .focusable()
            .onKeyEvent(event -> {
                if (event.isUp()) {
                    ctrl.navigateOption(-1);
                    optionList.selected(ctrl.selectedOptionForActive());
                    return EventResult.HANDLED;
                }
                if (event.isDown()) {
                    ctrl.navigateOption(1);
                    optionList.selected(ctrl.selectedOptionForActive());
                    return EventResult.HANDLED;
                }
                if (event.isLeft()) {
                    ctrl.navigateQuestion(-1);
                    return EventResult.HANDLED;
                }
                if (event.isRight()) {
                    ctrl.navigateQuestion(1);
                    return EventResult.HANDLED;
                }
                if (event.isConfirm()) {
                    ctrl.confirmCurrentQuestion();
                    if (ctrl.allQuestionsAnswered()) {
                        onAllDone.run();
                    } else {
                        advanceToNextUnanswered(ctrl);
                    }
                    return EventResult.HANDLED;
                }
                // Number shortcuts
                for (int i = 0; i < active.options().length && i < 9; i++) {
                    if (event.isChar((char) ('1' + i))) {
                        ctrl.navigateOption(i - ctrl.selectedOptionForActive());
                        optionList.selected(i);
                        ctrl.confirmCurrentQuestion();
                        if (ctrl.allQuestionsAnswered()) {
                            onAllDone.run();
                        } else {
                            advanceToNextUnanswered(ctrl);
                        }
                        return EventResult.HANDLED;
                    }
                }
                return EventResult.UNHANDLED;
            }));

        if (confirmed[activeIdx] != null) {
            content.add(text("  \u2713 " + confirmed[activeIdx]).green());
        }
        content.add(navHint);

        int height = active.options().length + 6 + (confirmed[activeIdx] != null ? 1 : 0);

        return row(
            spacer(1),
            panel(
                row(spacer(1), column(content.toArray(Element[]::new)), spacer(1))
            ).rounded().borderColor(Color.YELLOW).fill(),
            spacer(1)
        ).length(Math.min(height, 20));
    }

    private static void advanceToNextUnanswered(CliController ctrl) {
        String[] answers = ctrl.confirmedAnswers();
        for (int i = 0; i < answers.length; i++) {
            if (answers[i] == null) {
                ctrl.navigateQuestion(i - ctrl.activeQuestionIdx());
                return;
            }
        }
    }
}
