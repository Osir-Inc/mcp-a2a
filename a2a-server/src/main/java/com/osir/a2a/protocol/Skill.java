package com.osir.a2a.protocol;

import java.util.List;

/**
 * A2A Skill - a capability advertised in an Agent Card.
 */
public class Skill {

    private String id;
    private String name;
    private String description;
    private List<String> tags;
    private List<String> examples;

    public Skill() {}

    public Skill(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
}
