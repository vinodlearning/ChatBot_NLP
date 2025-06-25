package com.ben.view.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced ParsedQuery model to hold comprehensive NLP analysis results
 *
 * @author Ben
 * @version 2.0.0
 */
public class ParsedQuery {
    
    private String intent;
    private double confidence;
    private Map<String, List<String>> entities;
    private String originalInput;
    private String processedInput;
    private long timestamp;
    private String processingId;
    private Map<String, Object> metadata;
    
    public ParsedQuery() {
        this.entities = new HashMap<>();
        this.metadata = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getIntent() {
        return intent;
    }
    
    public void setIntent(String intent) {
        this.intent = intent;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public Map<String, List<String>> getEntities() {
        return entities;
    }
    
    public void setEntities(Map<String, List<String>> entities) {
        this.entities = entities != null ? entities : new HashMap<>();
    }
    
    public String getOriginalInput() {
        return originalInput;
    }
    
    public void setOriginalInput(String originalInput) {
        this.originalInput = originalInput;
    }
    
    public String getProcessedInput() {
        return processedInput;
    }
    
    public void setProcessedInput(String processedInput) {
        this.processedInput = processedInput;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getProcessingId() {
        return processingId;
    }
    
    public void setProcessingId(String processingId) {
        this.processingId = processingId;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    // Utility methods - Fixed for Java 8 compatibility
    public boolean hasEntity(String entityType) {
        List<String> entityList = entities.get(entityType);
        return entityList != null && !entityList.isEmpty();
    }
    
    public List<String> getEntity(String entityType) {
        // Java 8 compatible version
        List<String> entityList = entities.get(entityType);
        return entityList != null ? entityList : Collections.emptyList();
    }
    
    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }
    
    public boolean isLowConfidence() {
        return confidence < 0.3;
    }
    
    @Override
    public String toString() {
        return String.format("ParsedQuery{intent='%s', confidence=%.2f, entities=%d types, input='%s'}", 
                           intent, confidence, entities.size(), 
                           originalInput != null ? originalInput.substring(0, Math.min(50, originalInput.length())) : "null");
    }
}
