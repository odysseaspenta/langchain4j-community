package dev.langchain4j.community.rag.benchmark.dataset;

/**
 * One corpus passage. Each passage becomes exactly one vector (PRD F4).
 */
public record Passage(String id, String title, String text) {

    /**
     * The text that is embedded and stored: {@code title + "\n" + text}, or just {@code text} without a title.
     */
    public String content() {
        return title == null || title.isBlank() ? text : title + "\n" + text;
    }
}
