package cz.krokviak.agents.tool;

public sealed interface ToolOutput permits ToolOutput.Text, ToolOutput.Image, ToolOutput.File {
    static Text text(String content) { return new Text(content); }
    static Image image(byte[] data, String mimeType) { return new Image(data, mimeType); }
    static File file(byte[] data, String name) { return new File(data, name); }
    record Text(String content) implements ToolOutput {}
    record Image(byte[] data, String mimeType) implements ToolOutput {}
    record File(byte[] data, String name) implements ToolOutput {}
}
