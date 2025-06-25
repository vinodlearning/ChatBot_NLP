package com.ben.view.service;

import com.ben.view.model.ParsedQuery;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

public class ChatbotNLPService {
    private static final String MODEL_BASE_PATH =
                "C:\\JDeveloper\\mywork\\BCCTChatBot\\BCCTChatBotUI\\public_html\\models\\";
    // NLP Models
    private TokenizerME tokenizer;
    private SentenceDetectorME sentenceDetector;
    private POSTaggerME posTagger;
    private NameFinderME personNameFinder;
    private NameFinderME organizationNameFinder;
    private NameFinderME locationNameFinder;
    private ChunkerME chunker;
    private DictionaryLemmatizer lemmatizer;
    
    // Pattern matchers
    private static final Pattern CONTRACT_PATTERN = Pattern.compile("\\b[A-Z]{2,3}\\d{3,6}\\b");
    private static final Pattern PART_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{3,6}\\b");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\b\\d{8,10}\\b");
    
    // Intent keywords
    private Map<String, Set<String>> intentKeywords;
    private Map<String, String> commonTypos;
    
    public ChatbotNLPService() {
        initializeModels();
        initializeIntentKeywords();
        initializeTypoCorrections();
    }
    
    private void initializeModels() {
        Map<String, String> modelFiles = new HashMap<>();
        modelFiles.put("tokenizer", "en-token.bin");
        modelFiles.put("pos", "en-pos-maxent.bin");
        modelFiles.put("sentence", "en-sent.bin");
        modelFiles.put("person", "en-ner-person.bin");
        modelFiles.put("organization", "en-ner-organization.bin");
        modelFiles.put("location", "en-ner-location.bin");
        modelFiles.put("chunker", "en-chunker.bin");
        modelFiles.put("lemmatizer", "en-lemmatizer.txt");
        
        try {
            // Load tokenizer
            TokenizerModel tokenizerModel = new TokenizerModel(getModelInputStream(modelFiles.get("tokenizer")));
            tokenizer = new TokenizerME(tokenizerModel);
            
            // Load sentence detector
            SentenceModel sentenceModel = new SentenceModel(getModelInputStream(modelFiles.get("sentence")));
            sentenceDetector = new SentenceDetectorME(sentenceModel);
            
            // Load POS tagger
            POSModel posModel = new POSModel(getModelInputStream(modelFiles.get("pos")));
            posTagger = new POSTaggerME(posModel);
            
            // Load NER models
            TokenNameFinderModel personModel = new TokenNameFinderModel(getModelInputStream(modelFiles.get("person")));
            personNameFinder = new NameFinderME(personModel);
            
            TokenNameFinderModel orgModel = new TokenNameFinderModel(getModelInputStream(modelFiles.get("organization")));
            organizationNameFinder = new NameFinderME(orgModel);
            
            TokenNameFinderModel locModel = new TokenNameFinderModel(getModelInputStream(modelFiles.get("location")));
            locationNameFinder = new NameFinderME(locModel);
            
            // Load chunker
            ChunkerModel chunkerModel = new ChunkerModel(getModelInputStream(modelFiles.get("chunker")));
            chunker = new ChunkerME(chunkerModel);
            
            // Load lemmatizer-getModelInputStream(modelFiles.get("lemmatizer")));
            lemmatizer = new DictionaryLemmatizer(getModelInputStream(modelFiles.get("lemmatizer")));
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load NLP models", e);
        }
    }
    
    private void initializeIntentKeywords() {
        intentKeywords = new HashMap<>();
        
        // Contract keywords
        intentKeywords.put("CONTRACT_INFO", new HashSet<>(Arrays.asList(
            "show", "contract", "details", "info", "find", "get", "display"
        )));
        
        // Parts keywords
        intentKeywords.put("PARTS_INFO", new HashSet<>(Arrays.asList(
            "parts", "part", "specifications", "spec", "datasheet", "compatible", 
            "stock", "lead", "time", "manufacturer", "issues", "defects", "warranty",
            "active", "discontinued"
        )));
        
        // Status keywords
        intentKeywords.put("STATUS_CHECK", new HashSet<>(Arrays.asList(
            "status", "expired", "active", "check", "state"
        )));
        
        // Customer keywords
        intentKeywords.put("CUSTOMER_INFO", new HashSet<>(Arrays.asList(
            "customer", "account", "boeing", "honeywell", "client"
        )));
        
        // Help keywords
        intentKeywords.put("HELP_CREATE", new HashSet<>(Arrays.asList(
            "create", "help", "how", "new", "make"
        )));
        
        // Failed parts keywords
        intentKeywords.put("FAILED_PARTS", new HashSet<>(Arrays.asList(
            "failed", "failure", "error", "defective", "broken"
        )));
    }
    
    private void initializeTypoCorrections() {
        commonTypos = new HashMap<>();
        commonTypos.put("cntrs", "contracts");
        commonTypos.put("cntr", "contract");
        commonTypos.put("shw", "show");
        commonTypos.put("contarct", "contract");
        commonTypos.put("contacrt", "contract");
        commonTypos.put("pasrt", "part");
        commonTypos.put("parst", "part");
        commonTypos.put("filed", "failed");
        commonTypos.put("crate", "create");
        commonTypos.put("contarctNume", "contract number");
    }
    
    public String processQuery(String userInput) {
        try {
            ParsedQuery parsedQuery = parseQuery(userInput);
            return executeQuery(parsedQuery);
        } catch (Exception e) {
            return "I'm sorry, I encountered an error processing your request: " + e.getMessage();
        }
    }
    
    public ParsedQuery parseQuery(String userInput) {
        ParsedQuery query = new ParsedQuery();
        query.setOriginalQuery(userInput);
        
        // Step 1: Correct common typos
        String correctedInput = correctTypos(userInput.toLowerCase());
        query.setCorrectedQuery(correctedInput);
        
        // Step 2: Tokenize and analyze
        String[] tokens = tokenizer.tokenize(correctedInput);
        String[] posTags = posTagger.tag(tokens);
        
        // Step 3: Extract entities
        extractEntities(query, tokens, correctedInput);
        
        // Step 4: Determine intent
        determineIntent(query, tokens, posTags);
        
        // Step 5: Calculate confidence
        query.setConfidence(calculateConfidence(query, tokens));
        
        return query;
    }
    
    private String correctTypos(String input) {
        String corrected = input;
        for (Map.Entry<String, String> typo : commonTypos.entrySet()) {
            corrected = corrected.replaceAll("\\b" + typo.getKey() + "\\b", typo.getValue());
        }
        return corrected;
    }
    
    private void extractEntities(ParsedQuery query, String[] tokens, String input) {
        // Extract contract numbers
        Matcher contractMatcher = CONTRACT_PATTERN.matcher(input.toUpperCase());
        if (contractMatcher.find()) {
            query.setContractNumber(contractMatcher.group());
        }
        
        // Extract part numbers
        Matcher partMatcher = PART_PATTERN.matcher(input.toUpperCase());
        if (partMatcher.find()) {
            query.setPartNumber(partMatcher.group());
        }
        
        // Extract account numbers
        Matcher accountMatcher = ACCOUNT_PATTERN.matcher(input);
        if (accountMatcher.find()) {
            query.setAccountNumber(accountMatcher.group());
        }
        
        // Extract person names
        Span[] personSpans = personNameFinder.find(tokens);
        if (personSpans.length > 0) {
            StringBuilder personName = new StringBuilder();
            for (int i = personSpans[0].getStart(); i < personSpans[0].getEnd(); i++) {
                personName.append(tokens[i]).append(" ");
            }
            query.setUserName(personName.toString().trim());
        }
        
        // Extract organization names
        Span[] orgSpans = organizationNameFinder.find(tokens);
        if (orgSpans.length > 0) {
            StringBuilder orgName = new StringBuilder();
            for (int i = orgSpans[0].getStart(); i < orgSpans[0].getEnd(); i++) {
                orgName.append(tokens[i]).append(" ");
            }
            query.setCustomerName(orgName.toString().trim());
        }
    }
    
    private void determineIntent(ParsedQuery query, String[] tokens, String[] posTags) {
        Map<String, Integer> intentScores = new HashMap<>();
        
        // Score each intent based on keyword matches
        for (String token : tokens) {
            for (Map.Entry<String, Set<String>> intent : intentKeywords.entrySet()) {
                if (intent.getValue().contains(token.toLowerCase())) {
                    intentScores.put(intent.getKey(), intentScores.getOrDefault(intent.getKey(), 0) + 1);
                }
            }
        }
        
        // Determine primary intent and action
        String topIntent = intentScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
        
        // Set query type and action based on intent and context
        setQueryTypeAndAction(query, topIntent, tokens);
    }
    
        private void setQueryTypeAndAction(ParsedQuery query, String intent, String[] tokens) {
        String tokenString = String.join(" ", tokens).toLowerCase();
        
        switch (intent) {
            case "CONTRACT_INFO":
                query.setQueryType(ParsedQuery.QueryType.CONTRACT_INFO);
                if (tokenString.contains("show") || tokenString.contains("display")) {
                    query.setActionType(ParsedQuery.ActionType.SHOW);
                } else if (tokenString.contains("details")) {
                    query.setActionType(ParsedQuery.ActionType.DETAILS);
                } else {
                    query.setActionType(ParsedQuery.ActionType.INFO);
                }
                break;
                
            case "PARTS_INFO":
                if (tokenString.contains("failed") || tokenString.contains("failure") || tokenString.contains("error")) {
                    query.setQueryType(ParsedQuery.QueryType.FAILED_PARTS);
                    query.setActionType(ParsedQuery.ActionType.LIST);
                } else if (tokenString.contains("contract") && query.getPartNumber() == null) {
                    query.setQueryType(ParsedQuery.QueryType.PARTS_BY_CONTRACT);
                    query.setActionType(ParsedQuery.ActionType.LIST);
                } else {
                    query.setQueryType(ParsedQuery.QueryType.PARTS_INFO);
                    determinePartsAction(query, tokenString);
                }
                break;
                
            case "STATUS_CHECK":
                query.setQueryType(ParsedQuery.QueryType.STATUS_CHECK);
                query.setActionType(ParsedQuery.ActionType.CHECK_STATUS);
                if (tokenString.contains("expired")) {
                    query.setStatusType("expired");
                } else if (tokenString.contains("active")) {
                    query.setStatusType("active");
                }
                break;
                
            case "CUSTOMER_INFO":
                query.setQueryType(ParsedQuery.QueryType.CUSTOMER_INFO);
                query.setActionType(ParsedQuery.ActionType.INFO);
                break;
                
            case "HELP_CREATE":
                query.setQueryType(ParsedQuery.QueryType.HELP_CREATE_CONTRACT);
                query.setActionType(ParsedQuery.ActionType.CREATE);
                break;
                
            case "FAILED_PARTS":
                query.setQueryType(ParsedQuery.QueryType.FAILED_PARTS);
                query.setActionType(ParsedQuery.ActionType.LIST);
                break;
                
            default:
                query.setQueryType(ParsedQuery.QueryType.UNKNOWN);
                query.setActionType(ParsedQuery.ActionType.INFO);
        }
    }
    
    private void determinePartsAction(ParsedQuery query, String tokenString) {
        if (tokenString.contains("specifications") || tokenString.contains("spec")) {
            query.setActionType(ParsedQuery.ActionType.GET_SPECIFICATIONS);
        } else if (tokenString.contains("active") || tokenString.contains("discontinued")) {
            query.setActionType(ParsedQuery.ActionType.CHECK_ACTIVE);
        } else if (tokenString.contains("datasheet")) {
            query.setActionType(ParsedQuery.ActionType.GET_DATASHEET);
        } else if (tokenString.contains("compatible")) {
            query.setActionType(ParsedQuery.ActionType.GET_COMPATIBLE);
        } else if (tokenString.contains("stock")) {
            query.setActionType(ParsedQuery.ActionType.CHECK_STOCK);
        } else if (tokenString.contains("lead") && tokenString.contains("time")) {
            query.setActionType(ParsedQuery.ActionType.GET_LEAD_TIME);
        } else if (tokenString.contains("manufacturer")) {
            query.setActionType(ParsedQuery.ActionType.GET_MANUFACTURER);
        } else if (tokenString.contains("issues") || tokenString.contains("defects")) {
            query.setActionType(ParsedQuery.ActionType.CHECK_ISSUES);
        } else if (tokenString.contains("warranty")) {
            query.setActionType(ParsedQuery.ActionType.GET_WARRANTY);
        } else {
            query.setActionType(ParsedQuery.ActionType.INFO);
        }
    }
    
    private double calculateConfidence(ParsedQuery query, String[] tokens) {
        double confidence = 0.5; // Base confidence
        
        // Increase confidence if we found specific entities
        if (query.getContractNumber() != null) confidence += 0.2;
        if (query.getPartNumber() != null) confidence += 0.2;
        if (query.getUserName() != null) confidence += 0.1;
        if (query.getCustomerName() != null) confidence += 0.1;
        
        // Increase confidence if query type is not unknown
        if (query.getQueryType() != ParsedQuery.QueryType.UNKNOWN) confidence += 0.2;
        
        return Math.min(confidence, 1.0);
    }
    
    private String executeQuery(ParsedQuery query) {
        // Use the enum directly without quotes
        switch (query.getQueryType()) {
            case CONTRACT_INFO:
                return handleContractInfo(query);
            case USER_CONTRACT_QUERY:
                return handleUserContractQuery(query);
            case STATUS_CHECK:
                return handleStatusCheck(query);
            case CUSTOMER_INFO:
                return handleCustomerInfo(query);
            case PARTS_INFO:
                return handlePartsInfo(query);
            case PARTS_BY_CONTRACT:
                return handlePartsByContract(query);
            case FAILED_PARTS:
                return handleFailedParts(query);
            case HELP_CREATE_CONTRACT:
                return handleCreateContractHelp(query);
            case UNKNOWN:
                return "❓ I'm sorry, I didn't understand your request. Could you please rephrase it?\n\n" +
                       "💡 Try asking:\n" +
                       "• 'Show contract ABC123'\n" +
                       "• 'Parts for contract XYZ456'\n" +
                       "• 'Status of part AE125'\n" +
                       "• 'Customer info for Boeing'";
            default:
                return "❓ I'm sorry, I didn't understand your request. Could you please rephrase it?";
        }
    }
    
    // Wrapper methods for different query types
    private String handleContractInfo(ParsedQuery query) {
        if (query.getContractNumber() != null) {
            return contractByContractNumber(query.getContractNumber());
        }
        return "Please provide a contract number to get contract information.";
    }
    
    private String handleUserContractQuery(ParsedQuery query) {
        if (query.getUserName() != null) {
            return contractByUser(query.getUserName());
        }
        return "Please specify a user name to search for contracts.";
    }
    
    private String handleStatusCheck(ParsedQuery query) {
        if (query.getContractNumber() != null) {
            return checkContractStatus(query.getContractNumber());
        } else if (query.getStatusType() != null) {
            return getContractsByStatus(query.getStatusType());
        }
        return "Please specify a contract number or status type.";
    }
    
    private String handleCustomerInfo(ParsedQuery query) {
        if (query.getCustomerName() != null) {
            return getCustomerContracts(query.getCustomerName());
        } else if (query.getAccountNumber() != null) {
            return getCustomerByAccount(query.getAccountNumber());
        }
        return "Please specify a customer name or account number.";
    }
    
    private String handlePartsInfo(ParsedQuery query) {
        if (query.getPartNumber() == null) {
            return "Please specify a part number.";
        }
        
        switch (query.getActionType()) {
            case GET_SPECIFICATIONS:
                return getPartSpecifications(query.getPartNumber());
            case CHECK_ACTIVE:
                return checkPartStatus(query.getPartNumber());
            case GET_DATASHEET:
                return getPartDatasheet(query.getPartNumber());
            case GET_COMPATIBLE:
                return getCompatibleParts(query.getPartNumber());
            case CHECK_STOCK:
                return checkPartStock(query.getPartNumber());
            case GET_LEAD_TIME:
                return getPartLeadTime(query.getPartNumber());
            case GET_MANUFACTURER:
                return getPartManufacturer(query.getPartNumber());
            case CHECK_ISSUES:
                return getPartIssues(query.getPartNumber());
            case GET_WARRANTY:
                return getPartWarranty(query.getPartNumber());
            default:
                return pullPartsInfoByPartsNumber(query.getPartNumber());
        }
    }
    
    private String handlePartsByContract(ParsedQuery query) {
        if (query.getContractNumber() != null) {
            return pullPartsByContract(query.getContractNumber());
        }
        return "Please specify a contract number to get parts information.";
    }
    
    private String handleFailedParts(ParsedQuery query) {
        if (query.getContractNumber() != null) {
            return failedPartsByContract(query.getContractNumber());
        } else if (query.getPartNumber() != null) {
            return isPartsFailed(query.getPartNumber());
        } else {
            return failedParts();
        }
    }
    
    private String handleCreateContractHelp(ParsedQuery query) {
        return createContractHelp();
    }
    
    // Business logic wrapper methods - these will contain your database calls
    
    /**
     * Get contract information by contract number
     */
    public String contractByContractNumber(String contractNumber) {
        // TODO: Implement database call to get contract details
        return "Retrieving contract information for: " + contractNumber;
    }
    
    /**
     * Get contracts by user name
     */
    public String contractByUser(String userName) {
        // TODO: Implement database call to get user's contracts
        return "Retrieving contracts for user: " + userName;
    }
    
    /**
     * Get contracts by date range
     */
    public String contractByDates(String dateRange) {
        // TODO: Implement database call to get contracts by date
        return "Retrieving contracts for date range: " + dateRange;
    }
    
    /**
     * Check contract status
     */
    public String checkContractStatus(String contractNumber) {
        // TODO: Implement database call to check contract status
        return "Checking status for contract: " + contractNumber;
    }
    
    /**
     * Get contracts by status type
     */
    public String getContractsByStatus(String statusType) {
        // TODO: Implement database call to get contracts by status
        return "Retrieving " + statusType + " contracts";
    }
    
    /**
     * Get customer contracts
     */
    public String getCustomerContracts(String customerName) {
        // TODO: Implement database call to get customer contracts
        return "Retrieving contracts for customer: " + customerName;
    }
    
    /**
     * Get customer by account number
     */
    public String getCustomerByAccount(String accountNumber) {
        // TODO: Implement database call to get customer by account
        return "Retrieving customer information for account: " + accountNumber;
    }
    
    /**
     * Get parts by contract number
     */
    public String pullPartsByContract(String contractNumber) {
        // TODO: Implement database call to get parts by contract
        return "Retrieving parts for contract: " + contractNumber;
    }
    
    /**
     * Get part information by part number
     */
    public String pullPartsInfoByPartsNumber(String partNumber) {
        // TODO: Implement database call to get part information
        return "Retrieving information for part: " + partNumber;
    }
    
    /**
     * Get failed parts by contract
     */
    public String failedPartsByContract(String contractNumber) {
        // TODO: Implement database call to get failed parts by contract
        return "Retrieving failed parts for contract: " + contractNumber;
    }
    
    /**
     * Get all failed parts
     */
    public String failedParts() {
        // TODO: Implement database call to get all failed parts
        return "Retrieving all failed parts";
    }
    
    /**
     * Check if specific part has failed
     */
    public String isPartsFailed(String partNumber) {
        // TODO: Implement database call to check if part failed
        return "Checking failure status for part: " + partNumber;
    }
    
    /**
     * Get part specifications
     */
    public String getPartSpecifications(String partNumber) {
        // TODO: Implement database call to get part specifications
        return "Retrieving specifications for part: " + partNumber;
    }
    
    /**
     * Check if part is active or discontinued
     */
    public String checkPartStatus(String partNumber) {
        // TODO: Implement database call to check part status
        return "Checking active status for part: " + partNumber;
    }
    
    /**
     * Get part datasheet
     */
    public String getPartDatasheet(String partNumber) {
        // TODO: Implement database call to get part datasheet
        return "Retrieving datasheet for part: " + partNumber;
    }
    
    /**
     * Get compatible parts
     */
    public String getCompatibleParts(String partNumber) {
        // TODO: Implement database call to get compatible parts
        return "Retrieving compatible parts for: " + partNumber;
    }
    
    /**
     * Check part stock availability
     */
    public String checkPartStock(String partNumber) {
        // TODO: Implement database call to check stock
        return "Checking stock availability for part: " + partNumber;
    }
    
    /**
     * Get part lead time
     */
    public String getPartLeadTime(String partNumber) {
        // TODO: Implement database call to get lead time
        return "Retrieving lead time for part: " + partNumber;
    }
    
    /**
     * Get part manufacturer
     */
    public String getPartManufacturer(String partNumber) {
        // TODO: Implement database call to get manufacturer
        return "Retrieving manufacturer for part: " + partNumber;
    }
    
    /**
     * Get part issues/defects
     */
    public String getPartIssues(String partNumber) {
        // TODO: Implement database call to get part issues
        return "Retrieving known issues for part: " + partNumber;
    }
    
    /**
     * Get part warranty information
     */
    public String getPartWarranty(String partNumber) {
        // TODO: Implement database call to get warranty info
        return "Retrieving warranty information for part: " + partNumber;
    }
    
    /**
     * Provide help for creating contracts
     */
    public String createContractHelp() {
        return "To create a new contract, please follow these steps:\n" +
               "1. Navigate to the Contract Creation page\n" +
               "2. Fill in the required fields (Customer, Start Date, End Date)\n" +
               "3. Add contract items and parts\n" +
               "4. Review and submit for approval\n" +
               "Would you like me to guide you through any specific step?";
    }
    private InputStream getModelInputStream(String fileName) {
           
            if (fileName != null) {
                try {
                    String fullPath = MODEL_BASE_PATH + fileName;
                    File modelFile = new File(fullPath);
                    System.out.println("=== Diagnosing: " + fileName + " ===");
                    System.out.println("Exists: " + modelFile.exists());
                    System.out.println("Size: " + modelFile.length() + " bytes");
                    System.out.println("Readable: " + modelFile.canRead());
                    
                    // Verify file before opening
                    if (!modelFile.exists()) {
                        System.err.println("File does not exist: " + fullPath);
                        return null;
                    }
                    if (!modelFile.canRead()) {
                        System.err.println("Cannot read file: " + fullPath);
                        return null;
                    }
                    if (modelFile.length() == 0) {
                        System.err.println("File is empty: " + fullPath);
                        return null;
                    }
                    
                    return new FileInputStream(modelFile);
                } catch (Exception e) {
                    System.err.println("Error opening file " + fileName + ": " + e.getMessage());
                    return null;
                }
            }
            return null;
        }
}
