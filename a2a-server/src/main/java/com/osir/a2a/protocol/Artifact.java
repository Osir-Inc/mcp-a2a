package com.osir.a2a.protocol;

import java.util.List;
import java.util.Map;

/**
 * A2A Artifact - output produced by an agent task.
 */
public class Artifact {

    private String name;
    private String description;
    private List<Part> parts;
    private Map<String, Object> metadata;

    public Artifact() {}

    public Artifact(String name, String text) {
        this.name = name;
        this.parts = List.of(new TextPart(text));
    }

    public static Artifact ofData(String name, Map<String, Object> data) {
        Artifact a = new Artifact();
        a.name = name;
        a.parts = List.of(new DataPart(data));
        return a;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
