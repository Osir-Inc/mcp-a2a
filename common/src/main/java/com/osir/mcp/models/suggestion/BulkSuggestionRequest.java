package com.osir.mcp.models.suggestion;

import java.util.List;

public class BulkSuggestionRequest {
    private List<String> names;
    private String tlds;
    private String lang;
    private Boolean useNumbers;
    private Integer maxResultsPerName;

    public BulkSuggestionRequest() {}

    public BulkSuggestionRequest(List<String> names, String tlds, String lang, Boolean useNumbers, Integer maxResultsPerName) {
        this.names = names;
        this.tlds = tlds;
        this.lang = lang;
        this.useNumbers = useNumbers;
        this.maxResultsPerName = maxResultsPerName;
    }

    // Getters and Setters
    public List<String> getNames() { return names; }
    public void setNames(List<String> names) { this.names = names; }

    public String getTlds() { return tlds; }
    public void setTlds(String tlds) { this.tlds = tlds; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public Boolean getUseNumbers() { return useNumbers; }
    public void setUseNumbers(Boolean useNumbers) { this.useNumbers = useNumbers; }

    public Integer getMaxResultsPerName() { return maxResultsPerName; }
    public void setMaxResultsPerName(Integer maxResultsPerName) { this.maxResultsPerName = maxResultsPerName; }
}