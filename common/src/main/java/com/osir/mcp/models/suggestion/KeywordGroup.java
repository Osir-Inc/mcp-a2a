package com.osir.mcp.models.suggestion;

import java.util.List;

public class KeywordGroup {
    private String keyword;
    private List<DomainSuggestionResult> suggestions;

    public KeywordGroup() {}

    public KeywordGroup(String keyword, List<DomainSuggestionResult> suggestions) {
        this.keyword = keyword;
        this.suggestions = suggestions;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<DomainSuggestionResult> getSuggestions() { return suggestions; }
    public void setSuggestions(List<DomainSuggestionResult> suggestions) { this.suggestions = suggestions; }
}
