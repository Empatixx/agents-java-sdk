package cz.krokviak.agents.cli.render.tui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trie for command name prefix matching.
 * Supports fast lookup of all commands starting with a given prefix.
 */
public final class CommandTrie {

    private static class Node {
        final Map<Character, Node> children = new HashMap<>();
        String value; // non-null if this node completes a command name
        String description;
    }

    private final Node root = new Node();

    public void insert(String command, String description) {
        Node current = root;
        for (char c : command.toCharArray()) {
            current = current.children.computeIfAbsent(c, _ -> new Node());
        }
        current.value = command;
        current.description = description;
    }

    /** Find all commands matching the given prefix. */
    public List<Match> search(String prefix) {
        Node current = root;
        for (char c : prefix.toCharArray()) {
            current = current.children.get(c);
            if (current == null) return List.of();
        }
        List<Match> results = new ArrayList<>();
        collect(current, results);
        return results;
    }

    private void collect(Node node, List<Match> results) {
        if (node.value != null) {
            results.add(new Match(node.value, node.description));
        }
        for (var child : node.children.values()) {
            collect(child, results);
        }
    }

    public record Match(String command, String description) {}
}
