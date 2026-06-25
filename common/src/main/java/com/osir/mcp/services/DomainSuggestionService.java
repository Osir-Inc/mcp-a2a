package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.suggestion.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DomainSuggestionService {

    private static final Logger LOG = Logger.getLogger(DomainSuggestionService.class);

    @Inject
    @RestClient
    DomainBackendClient backendClient;

    /**
     * Get domain name suggestions based on input name
     */
    public DomainSuggestionsResult suggestDomains(String name, String tlds, String lang, Boolean useNumbers, Integer maxResults) {
        try {
            // Set defaults if not provided
            if (tlds == null) tlds = "com,net";
            if (lang == null) lang = "eng";
            if (useNumbers == null) useNumbers = true;
            if (maxResults == null) maxResults = 20;

            LOG.infof("Suggesting domains for name: %s, tlds: %s, lang: %s, useNumbers: %s, maxResults: %d", 
                name, tlds, lang, useNumbers, maxResults);

            DomainSuggestionResponse response = backendClient.suggestDomains(name, tlds, lang, useNumbers, maxResults);
            
            DomainSuggestionsResult result = new DomainSuggestionsResult(true, "Domain suggestions generated successfully");
            result.setSuggestions(response.getResults());
            
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error generating domain suggestions for name: %s", name);
            return new DomainSuggestionsResult(false, "Failed to generate domain suggestions: " + e.getMessage());
        }
    }

    /**
     * Generate domain suggestions by spinning words
     */
    public DomainSuggestionsResult spinWord(String name, Integer position, Double similarity, String tlds, String lang, Integer maxResults) {
        try {
            // Set defaults if not provided
            if (similarity == null) similarity = 0.6;
            if (tlds == null) tlds = "com,net";
            if (lang == null) lang = "eng";
            if (maxResults == null) maxResults = 20;

            LOG.infof("Spinning words for name: %s, position: %s, similarity: %f, tlds: %s", 
                name, position, similarity, tlds);

            DomainSuggestionResponse response = backendClient.spinWord(name, position, similarity, tlds, lang, maxResults);
            
            DomainSuggestionsResult result = new DomainSuggestionsResult(true, "Word spin suggestions generated successfully");
            result.setSuggestions(response.getResults());
            
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error generating word spin suggestions for name: %s", name);
            return new DomainSuggestionsResult(false, "Failed to generate word spin suggestions: " + e.getMessage());
        }
    }

    /**
     * Generate domain suggestions by adding prefixes
     */
    public DomainSuggestionsResult addPrefix(String name, String vocabulary, String tlds, String lang, Integer maxResults) {
        try {
            // Set defaults if not provided
            if (vocabulary == null) vocabulary = "@prefixes";
            if (tlds == null) tlds = "com,net";
            if (lang == null) lang = "eng";
            if (maxResults == null) maxResults = 20;

            LOG.infof("Adding prefixes to name: %s, vocabulary: %s, tlds: %s", 
                name, vocabulary, tlds);

            DomainSuggestionResponse response = backendClient.addPrefix(name, vocabulary, tlds, lang, maxResults);
            
            DomainSuggestionsResult result = new DomainSuggestionsResult(true, "Prefix suggestions generated successfully");
            result.setSuggestions(response.getResults());
            
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error generating prefix suggestions for name: %s", name);
            return new DomainSuggestionsResult(false, "Failed to generate prefix suggestions: " + e.getMessage());
        }
    }

    /**
     * Generate domain suggestions by adding suffixes
     */
    public DomainSuggestionsResult addSuffix(String name, String vocabulary, String tlds, String lang, Integer maxResults) {
        try {
            // Set defaults if not provided
            if (vocabulary == null) vocabulary = "@suffixes";
            if (tlds == null) tlds = "com,net";
            if (lang == null) lang = "eng";
            if (maxResults == null) maxResults = 20;

            LOG.infof("Adding suffixes to name: %s, vocabulary: %s, tlds: %s", 
                name, vocabulary, tlds);

            DomainSuggestionResponse response = backendClient.addSuffix(name, vocabulary, tlds, lang, maxResults);
            
            DomainSuggestionsResult result = new DomainSuggestionsResult(true, "Suffix suggestions generated successfully");
            result.setSuggestions(response.getResults());
            
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error generating suffix suggestions for name: %s", name);
            return new DomainSuggestionsResult(false, "Failed to generate suffix suggestions: " + e.getMessage());
        }
    }

    /**
     * Bulk domain suggestions using the dedicated backend endpoint. Returns results grouped by keyword.
     */
    public BulkDomainSuggestionsResult bulkSuggestions(List<String> keywords, List<String> tlds, String lang, Integer maxResults) {
        try {
            if (keywords == null || keywords.isEmpty()) {
                return new BulkDomainSuggestionsResult(false, "No keywords provided for bulk suggestions");
            }

            if (tlds == null || tlds.isEmpty()) tlds = List.of("com", "net", "tech");
            if (lang == null) lang = "eng";
            if (maxResults == null) maxResults = 20;
            maxResults = Math.min(50, maxResults);

            LOG.infof("Generating bulk suggestions for %d keywords, %d tlds", keywords.size(), tlds.size());

            String tldsString = String.join(",", tlds);
            BulkSuggestionRequest request = new BulkSuggestionRequest(keywords, tldsString, lang, true, maxResults);
            BulkSuggestionResponse response = backendClient.bulkSuggest(request);

            List<KeywordGroup> groups = new ArrayList<>();
            Set<String> returnedTldSet = new LinkedHashSet<>();

            if (response.getSuggestions() != null) {
                for (BulkSuggestionResponse.BulkSuggestionItem item : response.getSuggestions()) {
                    List<DomainSuggestionResult> suggestions =
                            item.getSuggestions() != null ? item.getSuggestions() : Collections.emptyList();
                    groups.add(new KeywordGroup(item.getOriginalName(), suggestions));
                    for (DomainSuggestionResult s : suggestions) {
                        if (s.getName() != null && s.getName().contains(".")) {
                            returnedTldSet.add(s.getName().substring(s.getName().lastIndexOf('.') + 1).toLowerCase());
                        }
                    }
                }
            }

            List<String> errors = new ArrayList<>();
            if (response.getErrors() != null) {
                for (BulkSuggestionResponse.BulkSuggestionError e : response.getErrors()) {
                    errors.add(e.getOriginalName() + ": " + e.getErrorMessage());
                }
            }

            BulkDomainSuggestionsResult result = new BulkDomainSuggestionsResult(true,
                    "Bulk suggestions generated for " + groups.size() + " keyword(s)");
            result.setGroups(groups);
            result.setRequestedTlds(new ArrayList<>(tlds));
            result.setReturnedTlds(new ArrayList<>(returnedTldSet));
            result.setErrors(errors);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error generating bulk suggestions for %d keywords", keywords != null ? keywords.size() : 0);
            return new BulkDomainSuggestionsResult(false, "Failed to generate bulk suggestions: " + e.getMessage());
        }
    }

    /**
     * Check keyword availability across all supported TLDs
     */
    public KeywordAvailabilityResponse checkKeywordAvailability(String keyword, String registries, String tlds) {
        try {
            LOG.infof("Checking keyword availability for: %s, registries: %s, tlds: %s", keyword, registries, tlds);
            
            return backendClient.checkKeywordAvailability(keyword, registries, tlds);
        } catch (Exception e) {
            LOG.errorf(e, "Error checking keyword availability for: %s", keyword);
            throw new RuntimeException("Failed to check keyword availability: " + e.getMessage());
        }
    }

    /**
     * Check keyword availability summary (without detailed results)
     */
    public KeywordAvailabilityResponse checkKeywordAvailabilitySummary(String keyword, String registries, String tlds) {
        try {
            LOG.infof("Checking keyword availability summary for: %s, registries: %s, tlds: %s", keyword, registries, tlds);
            
            return backendClient.checkKeywordAvailabilitySummary(keyword, registries, tlds);
        } catch (Exception e) {
            LOG.errorf(e, "Error checking keyword availability summary for: %s", keyword);
            throw new RuntimeException("Failed to check keyword availability summary: " + e.getMessage());
        }
    }
}