package com.ben.view.service;

import com.ben.view.model.ParsedQuery;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

/**
 * Advanced NLP Service for Chatbot using Apache OpenNLP
 * Handles intent detection, entity extraction, and query parsing
 *
 * @author Ben
 * @version 2.0.0
 */
public class ChatbotNLPService {

    private static final Logger LOGGER = Logger.getLogger(ChatbotNLPService.class.getName());
    private static volatile ChatbotNLPService instance;

    // OpenNLP Models
    private TokenizerME tokenizer;
    private POSTaggerME posTagger;
    private SentenceDetectorME sentenceDetector;
    private NameFinderME personNameFinder;
    private NameFinderME organizationNameFinder;
    private NameFinderME locationNameFinder;
    private ChunkerME chunker;
    private DictionaryLemmatizer lemmatizer;

    // Configuration Constants
    private static final String MODEL_BASE_PATH =
        "C:\\JDeveloper\\mywork\\BCCTChatBot\\BCCTChatBotUI\\src\\com\\ben\\view\\service\\models\\";
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int MAX_INPUT_LENGTH = 1000;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.1;

    // Model file mappings
    private static final Map<String, String> MODEL_FILES;
    static {
        Map<String, String> modelFiles = new HashMap<>();
        modelFiles.put("tokenizer", "en-token.bin");
        modelFiles.put("pos", "en-pos-maxent.bin");
        modelFiles.put("sentence", "en-sent.bin");
        modelFiles.put("person", "en-ner-person.bin");
        modelFiles.put("organization", "en-ner-organization.bin");
        modelFiles.put("location", "en-ner-location.bin");
        modelFiles.put("chunker", "en-chunker.bin");
        modelFiles.put("lemmatizer", "en-lemmatizer.txt");
        MODEL_FILES = Collections.unmodifiableMap(modelFiles);
    }


    // Cache and Performance Tracking
    private final Map<String, ParsedQuery> queryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> processingTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    // Configuration Maps
    private final Map<String, Double> intentConfidenceThresholds;
    private final Map<String, List<String>> intentKeywords;
    private final Map<String, String> spellingCorrections;
    private final Map<String, List<Pattern>> intentPatterns;
    private final Set<String> stopWords;

    /**
     * Private constructor for singleton pattern
     */
    private ChatbotNLPService() {
        LOGGER.info("Initializing ChatbotNLPService...");

        this.intentConfidenceThresholds = initializeConfidenceThresholds();
        this.intentKeywords = initializeIntentKeywords();
        this.spellingCorrections = initializeSpellingCorrections();
        this.intentPatterns = initializeIntentPatterns();
        this.stopWords = initializeStopWords();

        initializeOpenNLPModels();

        LOGGER.info("ChatbotNLPService initialization completed");
    }

    /**
     * Thread-safe singleton instance getter
     */
    public static ChatbotNLPService getInstance() {
        if (instance == null) {
            synchronized (ChatbotNLPService.class) {
                if (instance == null) {
                    instance = new ChatbotNLPService();
                }
            }
        }
        return instance;
    }

    /**
     * Main method to parse user query with comprehensive processing
     */
    public ParsedQuery parseQuery(String input) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        try {
            // Input validation
            if (input == null || input.trim().isEmpty()) {
                return createErrorResponse("Input cannot be empty", input);
            }

            if (input.length() > MAX_INPUT_LENGTH) {
                return createErrorResponse("Input too long. Maximum " + MAX_INPUT_LENGTH + " characters allowed",
                                           input);
            }

            // Check cache first
            String cacheKey = generateCacheKey(input);
            ParsedQuery cachedResult = queryCache.get(cacheKey);
            if (cachedResult != null) {
                cacheHits.incrementAndGet();
                LOGGER.fine("Cache hit for query: " + input.substring(0, Math.min(50, input.length())));
                return cachedResult;
            }

            // Process the query
            ParsedQuery result = processQueryInternal(input);

            // Cache the result
            cacheQuery(cacheKey, result);

            // Record processing time
            long processingTime = System.currentTimeMillis() - startTime;
            processingTimes.put(result.getIntent(), processingTime);

            LOGGER.fine(String.format("Query processed in %dms: %s -> %s (%.2f)", processingTime,
                                      input.substring(0, Math.min(30, input.length())), result.getIntent(),
                                      result.getConfidence()));

            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing query: " + input, e);
            return createErrorResponse("Error processing query: " + e.getMessage(), input);
        }
    }

    /**
     * Internal query processing logic
     */
    private ParsedQuery processQueryInternal(String input) {
        // Step 1: Preprocessing
        String normalizedInput = preprocessInput(input);

        // Step 2: Sentence detection
        String[] sentences = detectSentences(normalizedInput);

        // Step 3: Process each sentence
        List<ProcessedSentence> processedSentences = new ArrayList<>();
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                processedSentences.add(processSentence(sentence));
            }
        }

        // Step 4: Intent detection and entity extraction
        String intent = determineIntent(processedSentences, normalizedInput);
        Map<String, List<String>> entities = combineEntities(processedSentences);
        double confidence = calculateConfidence(processedSentences, intent, normalizedInput);

        // Step 5: Post-processing and validation
        intent = validateAndRefineIntent(intent, entities, confidence);
        entities = cleanAndValidateEntities(entities);

        // Step 6: Create comprehensive response
        ParsedQuery result = createParsedQuery(intent, confidence, entities, normalizedInput, input);
        enrichParsedQuery(result, processedSentences);

        return result;
    }

    /**
     * Initialize all OpenNLP models with comprehensive error handling
     */
    private void initializeOpenNLPModels() {
        Map<String, Boolean> loadStatus = new HashMap<>();

        LOGGER.info("Loading OpenNLP models...");

        // Load core models
        loadStatus.put("tokenizer", loadTokenizer());
        loadStatus.put("pos", loadPOSTagger());
        loadStatus.put("sentence", loadSentenceDetector());
        loadStatus.put("chunker", loadChunker());

        // Load NER models
        loadStatus.put("person", loadPersonNameFinder());
        loadStatus.put("organization", loadOrganizationNameFinder());
        loadStatus.put("location", loadLocationNameFinder());

        // Load lemmatizer
        loadStatus.put("lemmatizer", loadLemmatizer());

        // Log loading results
        long successCount = loadStatus.values()
                                      .stream()
                                      .mapToLong(b -> b ? 1 : 0)
                                      .sum();
        LOGGER.info(String.format("Loaded %d/%d OpenNLP models successfully", successCount, loadStatus.size()));

        // Log individual model status
        loadStatus.forEach((model, loaded) -> { LOGGER.info(String.format("Model %s: %s", model,
                                                                          loaded ? "LOADED" : "FAILED")); });

        if (successCount == 0) {
            LOGGER.warning("No OpenNLP models loaded. Using fallback processing only.");
        }
    }

    /**
     * Load tokenizer model
     */
    private boolean loadTokenizer() {
        try (InputStream modelIn = getModelInputStream("tokenizer")) {
            if (modelIn != null) {
                TokenizerModel model = new TokenizerModel(modelIn);
                this.tokenizer = new TokenizerME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load tokenizer model", e);
        }
        return false;
    }

    /**
     * Load POS tagger model
     */
    private boolean loadPOSTagger() {
        try (InputStream modelIn = getModelInputStream("pos")) {
            if (modelIn != null) {
                POSModel model = new POSModel(modelIn);
                this.posTagger = new POSTaggerME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load POS tagger model", e);
        }
        return false;
    }

    /**
     * Load sentence detector model
     */
    private boolean loadSentenceDetector() {
        try (InputStream modelIn = getModelInputStream("sentence")) {
            if (modelIn != null) {
                SentenceModel model = new SentenceModel(modelIn);
                this.sentenceDetector = new SentenceDetectorME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load sentence detector model", e);
        }
        return false;
    }

    /**
     * Load chunker model
     */
    private boolean loadChunker() {
        try (InputStream modelIn = getModelInputStream("chunker")) {
            if (modelIn != null) {
                ChunkerModel model = new ChunkerModel(modelIn);
                this.chunker = new ChunkerME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load chunker model", e);
        }
        return false;
    }

    /**
     * Load person name finder model
     */
    private boolean loadPersonNameFinder() {
        try (InputStream modelIn = getModelInputStream("person")) {
            if (modelIn != null) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                this.personNameFinder = new NameFinderME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load person name finder model", e);
        }
        return false;
    }

    /**
     * Load organization name finder model
     */
    private boolean loadOrganizationNameFinder() {
        try (InputStream modelIn = getModelInputStream("organization")) {
            if (modelIn != null) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                this.organizationNameFinder = new NameFinderME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load organization name finder model", e);
        }
        return false;
    }

    /**
     * Load location name finder model
     */
    private boolean loadLocationNameFinder() {
        try (InputStream modelIn = getModelInputStream("location")) {
            if (modelIn != null) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                this.locationNameFinder = new NameFinderME(model);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load location name finder model", e);
        }
        return false;
    }

    /**
     * Load lemmatizer
     */
    private boolean loadLemmatizer() {
        try (InputStream dictIn = getModelInputStream("lemmatizer")) {
            if (dictIn != null) {
                this.lemmatizer = new DictionaryLemmatizer(dictIn);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load lemmatizer dictionary", e);
        }
        return false;
    }

    /**
     * Get model input stream
     */


    //    private InputStream getModelInputStream(String modelKey) {
    //        String fileName = MODEL_FILES.get(modelKey);
    //        System.out.println("File Name-------------->"+fileName);
    //
    //        if (fileName != null) {
    //            try {
    //                String fullPath = MODEL_BASE_PATH + fileName;
    //                System.out.println("Full path: " + fullPath);
    //
    //                File modelFile = new File(fullPath);
    //                if (modelFile.exists()) {
    //                    return new FileInputStream(modelFile);
    //                } else {
    //                    System.err.println("Model file does not exist: " + fullPath);
    //                }
    //            } catch (FileNotFoundException e) {
    //                System.err.println("Dobbinddi---------Error loading model file: " + e.getMessage());
    //            }
    //        }
    //        return null;
    //    }
    private InputStream getModelInputStream(String modelKey) {
        String fileName = MODEL_FILES.get(modelKey);

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

                // This is completely safe - just reading
                return new FileInputStream(modelFile);

            } catch (Exception e) {
                System.err.println("Error opening file: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Enhanced preprocessing with comprehensive text normalization
     */
    private String preprocessInput(String input) {
        String processed = input.trim();

        // Convert to lowercase for processing (preserve original case in entities)
        String lowerProcessed = processed.toLowerCase();

        // Apply spelling corrections
        for (Map.Entry<String, String> correction : spellingCorrections.entrySet()) {
            lowerProcessed =
                lowerProcessed.replaceAll("(?i)\\b" + Pattern.quote(correction.getKey()) + "\\b",
                                          correction.getValue());
        }

        // Normalize contract number formats
        lowerProcessed = lowerProcessed.replaceAll("contract\\s*#\\s*(\\d+)", "contract $1");
        lowerProcessed = lowerProcessed.replaceAll("#(\\d{6})", "contract $1");
        lowerProcessed = lowerProcessed.replaceAll("contract(\\d{6,8})", "contract $1");

        // Normalize common abbreviations
        lowerProcessed = lowerProcessed.replaceAll("\\binfo\\b", "information");
        lowerProcessed = lowerProcessed.replaceAll("\\bdeets\\b", "details");
        lowerProcessed = lowerProcessed.replaceAll("\\bu\\b", "you");
        lowerProcessed = lowerProcessed.replaceAll("\\br\\b", "are");

        // Remove extra whitespace an
        // Remove extra whitespace and punctuation normalization
        lowerProcessed = lowerProcessed.replaceAll("\\s+", " ");
        lowerProcessed = lowerProcessed.replaceAll("[.!?]+", ".");
        lowerProcessed = lowerProcessed.trim();

        return lowerProcessed;
    }

    /**
     * Detect sentences using OpenNLP or enhanced fallback
     */
    private String[] detectSentences(String input) {
        if (sentenceDetector != null) {
            return sentenceDetector.sentDetect(input);
        } else {
            // Enhanced fallback sentence detection
            return Arrays.stream(input.split("[.!?]+"))
                         .map(String::trim)
                         .filter(s -> !s.isEmpty())
                         .toArray(String[]::new);
        }
    }

    /**
     * Process individual sentence with comprehensive analysis
     */
    private ProcessedSentence processSentence(String sentence) {
        // Tokenization
        String[] tokens = tokenizeText(sentence);

        // POS Tagging
        String[] posTags = getPOSTags(tokens);

        // Lemmatization
        String[] lemmas = getLemmas(tokens, posTags);

        // Chunking
        String[] chunks = getChunks(tokens, posTags);

        // Entity extraction
        Map<String, List<String>> entities = extractEntities(tokens, sentence);

        // Key phrase extraction
        List<String> keyPhrases = extractSentenceKeyPhrases(tokens, posTags, chunks);

        return new ProcessedSentence(tokens, posTags, lemmas, chunks, entities, keyPhrases, sentence);
    }

    /**
     * Enhanced tokenization
     */
    private String[] tokenizeText(String text) {
        if (tokenizer != null) {
            return tokenizer.tokenize(text);
        } else {
            // Enhanced fallback tokenization
            return text.toLowerCase()
                       .replaceAll("[^a-zA-Z0-9\\s]", " ")
                       .trim()
                       .split("\\s+");
        }
    }

    /**
     * Get POS tags with fallback
     */
    private String[] getPOSTags(String[] tokens) {
        if (posTagger != null) {
            return posTagger.tag(tokens);
        } else {
            // Enhanced fallback POS tagging
            return Arrays.stream(tokens)
                         .map(this::getFallbackPOSTag)
                         .toArray(String[]::new);
        }
    }

    /**
     * Get lemmas for tokens
     */
    private String[] getLemmas(String[] tokens, String[] posTags) {
        if (lemmatizer != null) {
            return lemmatizer.lemmatize(tokens, posTags);
        } else {
            // Simple fallback - return tokens as-is
            return Arrays.copyOf(tokens, tokens.length);
        }
    }

    /**
     * Get chunks for tokens
     */
    private String[] getChunks(String[] tokens, String[] posTags) {
        if (chunker != null) {
            return chunker.chunk(tokens, posTags);
        } else {
            // Simple fallback chunking
            String[] chunks = new String[tokens.length];
            Arrays.fill(chunks, "O");
            return chunks;
        }
    }

    /**
     * Fallback POS tagging logic
     */
    private String getFallbackPOSTag(String token) {
        if (token.matches("\\d+"))
            return "CD"; // Cardinal number
        if (token.matches(".*ing"))
            return "VBG"; // Gerund
        if (token.matches(".*ed"))
            return "VBD"; // Past tense verb
        if (token.matches(".*ly"))
            return "RB"; // Adverb
        if (Character.isUpperCase(token.charAt(0)))
            return "NNP"; // Proper noun
        if (Arrays.asList("the", "a", "an").contains(token.toLowerCase()))
            return "DT"; // Determiner
        if (Arrays.asList("show", "get", "find", "tell", "give").contains(token.toLowerCase()))
            return "VB"; // Verb
        return "NN"; // Default to noun
    }

    /**
     * Comprehensive entity extraction
     */
    private Map<String, List<String>> extractEntities(String[] tokens, String originalSentence) {
        Map<String, List<String>> entities = new HashMap<>();

        // Initialize entity lists
        entities.put("contractNumbers", extractContractNumbers(originalSentence));
        entities.put("customerNames", extractPersonNames(tokens));
        entities.put("organizationNames", extractOrganizationNames(tokens));
        entities.put("locations", extractLocationNames(tokens));
        entities.put("actions", extractActionWords(tokens));
        entities.put("accountNumbers", extractAccountNumbers(originalSentence));
        entities.put("userNames", extractUserNames(originalSentence));
        entities.put("dates", extractDates(originalSentence));
        entities.put("phoneNumbers", extractPhoneNumbers(originalSentence));
        entities.put("emails", extractEmails(originalSentence));

        return entities;
    }

    /**
     * Enhanced contract number extraction with validation
     */
    private List<String> extractContractNumbers(String text) {
        List<String> contractNumbers = new ArrayList<>();

        String[] patterns = {
            "(?:contract\\s+(?:number\\s+)?)(\\d{6,8})", "(?:agreement\\s+(?:number\\s+)?)(\\d{6,8})",
            "(?:deal\\s+(?:number\\s+)?)(\\d{6,8})", "(?:policy\\s+(?:number\\s+)?)(\\d{6,8})",
            "(?:^|\\s)(\\d{6})(?=\\s|$)", "(?:contract\\s+id\\s+)(\\d{6,8})", "(?:ref\\s+(?:number\\s+)?)(\\d{6,8})",
            "(?:contract#)(\\d{6,8})", "(?:contract)(\\d{6,8})", // Handle contractXXXXXX format
            "(?:info\\s+)(\\d{6,8})", // Handle "info 123456"
            "(?:details\\s+)(\\d{6,8})" // Handle "details 123456"

        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String number = matcher.group(1);
                if (isValidContractNumber(number) && !contractNumbers.contains(number)) {
                    contractNumbers.add(number);
                }
            }
        }

        return contractNumbers;
    }

    /**
     * Extract person names using OpenNLP NER
     */
    private List<String> extractPersonNames(String[] tokens) {
        List<String> names = new ArrayList<>();

        if (personNameFinder != null) {
            Span[] spans = personNameFinder.find(tokens);

            for (Span span : spans) {
                StringBuilder name = new StringBuilder();
                for (int i = span.getStart(); i < span.getEnd(); i++) {
                    name.append(tokens[i]).append(" ");
                }
                String fullName = name.toString().trim();
                if (isValidPersonName(fullName)) {
                    names.add(fullName);
                }
            }

            personNameFinder.clearAdaptiveData();
        } else {
            // Enhanced fallback person name detection
            names.addAll(extractPersonNamesFallback(tokens));
        }

        return names;
    }

    /**
     * Extract organization names
     */
    private List<String> extractOrganizationNames(String[] tokens) {
        List<String> organizations = new ArrayList<>();

        if (organizationNameFinder != null) {
            Span[] spans = organizationNameFinder.find(tokens);

            for (Span span : spans) {
                StringBuilder org = new StringBuilder();
                for (int i = span.getStart(); i < span.getEnd(); i++) {
                    org.append(tokens[i]).append(" ");
                }
                organizations.add(org.toString().trim());
            }

            organizationNameFinder.clearAdaptiveData();
        }

        return organizations;
    }

    /**
     * Extract location names
     */
    private List<String> extractLocationNames(String[] tokens) {
        List<String> locations = new ArrayList<>();

        if (locationNameFinder != null) {
            Span[] spans = locationNameFinder.find(tokens);

            for (Span span : spans) {
                StringBuilder location = new StringBuilder();
                for (int i = span.getStart(); i < span.getEnd(); i++) {
                    location.append(tokens[i]).append(" ");
                }
                locations.add(location.toString().trim());
            }

            locationNameFinder.clearAdaptiveData();
        }

        return locations;
    }


    /**
     * Enhanced action word extraction
     */
    private List<String> extractActionWords(String[] tokens) {
        List<String> actions = new ArrayList<>();
        Set<String> actionKeywords =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("show", "get", "find", "details", "info", "view",
                                                                    "display", "tell", "give", "provide", "retrieve",
                                                                    "fetch", "lookup", "check", "examine", "review",
                                                                    "see", "access", "pull", "what", "how", "where",
                                                                    "when", "which", "who", "data", "information",
                                                                    "content", "summary", "overview", "have", "contain",
                                                                    "include", "say", "state", "need", "want", "create",
                                                                    "update", "modify", "delete", "remove", "add",
                                                                    "search", "query", "list", "browse", "explore")));

        for (String token : tokens) {
            String lowerToken = token.toLowerCase();
            if (actionKeywords.contains(lowerToken) && !actions.contains(lowerToken)) {
                actions.add(lowerToken);
            }
        }

        return actions;
    }

    /**
     * Extract account numbers with enhanced patterns
     */
    private List<String> extractAccountNumbers(String text) {
        List<String> accountNumbers = new ArrayList<>();

        String[] patterns = {
            "(?:account\\s+(?:number\\s+)?)(\\d{8,12})", "(?:acc\\s+(?:no\\s+)?)(\\d{8,12})",
            "(?:account\\s+id\\s+)(\\d{8,12})", "(?:customer\\s+(?:number\\s+)?)(\\d{8,12})"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String number = matcher.group(1);
                if (isValidAccountNumber(number)) {
                    accountNumbers.add(number);
                }
            }
        }

        return accountNumbers;
    }

    /**
     * Extract user names with enhanced patterns
     */
    private List<String> extractUserNames(String text) {
        List<String> userNames = new ArrayList<>();

        String[] patterns = {
            "(?:user\\s+(?:name\\s+)?)(\\w+)", "(?:username\\s+)(\\w+)", "(?:user\\s+)(\\w+)",
            "(?:employee\\s+(?:id\\s+)?)(\\w+)", "(?:staff\\s+(?:id\\s+)?)(\\w+)"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String userName = matcher.group(1);
                if (isValidUserName(userName)) {
                    userNames.add(userName);
                }
            }
        }

        return userNames;
    }

    /**
     * Extract dates
     */
    private List<String> extractDates(String text) {
        List<String> dates = new ArrayList<>();

        String[] patterns = {
            "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b", "\\b(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b",
            "\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4})\\b",
            "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4})\\b"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                dates.add(matcher.group(1));
            }
        }

        return dates;
    }

    /**
     * Extract phone numbers
     */
    private List<String> extractPhoneNumbers(String text) {
        List<String> phoneNumbers = new ArrayList<>();

        String[] patterns = {
            "\\b(\\d{3}[.-]\\d{3}[.-]\\d{4})\\b", "\\b(\\(\\d{3}\\)\\s*\\d{3}[.-]\\d{4})\\b", "\\b(\\d{10})\\b" };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                phoneNumbers.add(matcher.group(1));
            }
        }

        return phoneNumbers;
    }

    /**
     * Extract email addresses
     */
    private List<String> extractEmails(String text) {
        List<String> emails = new ArrayList<>();

        Pattern pattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            emails.add(matcher.group());
        }

        return emails;
    }

    /**
     * Extract key phrases from sentence
     */
    private List<String> extractSentenceKeyPhrases(String[] tokens, String[] posTags, String[] chunks) {
        List<String> keyPhrases = new ArrayList<>();

        // Extract noun phrases using chunking information
        StringBuilder currentPhrase = new StringBuilder();
        for (int i = 0; i < tokens.length && i < chunks.length; i++) {
            String chunk = chunks[i];

            if (chunk.startsWith("B-NP") || chunk.startsWith("I-NP")) {
                // Part of noun phrase
                if (currentPhrase.length() > 0) {
                    currentPhrase.append(" ");
                }
                currentPhrase.append(currentPhrase.append(tokens[i]));
            } else {
                // End of noun phrase
                if (currentPhrase.length() > 0) {
                    String phrase = currentPhrase.toString().trim();
                    if (isValidKeyPhrase(phrase)) {
                        keyPhrases.add(phrase);
                    }
                    currentPhrase.setLength(0);
                }
            }
        }

        // Add final phrase if exists
        if (currentPhrase.length() > 0) {
            String phrase = currentPhrase.toString().trim();
            if (isValidKeyPhrase(phrase)) {
                keyPhrases.add(phrase);
            }
        }

        // Extract important single words
        for (int i = 0; i < tokens.length && i < posTags.length; i++) {
            String token = tokens[i];
            String pos = posTags[i];

            if ((pos.startsWith("NN") || pos.startsWith("VB")) && !stopWords.contains(token.toLowerCase()) &&
                token.length() > 2) {
                keyPhrases.add(token);
            }
        }

        return keyPhrases.stream()
                         .distinct()
                         .collect(Collectors.toList());
    }

    /**
     * Advanced intent determination with confidence scoring
     */
    private String determineIntent(List<ProcessedSentence> sentences, String originalInput) {
        Map<String, Double> intentScores = new HashMap<>();

        // Initialize all intent scores
        for (String intent : intentKeywords.keySet()) {
            intentScores.put(intent, 0.0);
        }

        // Score based on keyword matching
        for (ProcessedSentence sentence : sentences) {
            for (String token : sentence.getTokens()) {
                String lowerToken = token.toLowerCase();

                for (Map.Entry<String, List<String>> entry : intentKeywords.entrySet()) {
                    String intent = entry.getKey();
                    List<String> keywords = entry.getValue();

                    if (keywords.contains(lowerToken)) {
                        intentScores.merge(intent, 1.0, Double::sum);
                    }
                }
            }
        }

        // Score based on pattern matching
        for (Map.Entry<String, List<Pattern>> entry : intentPatterns.entrySet()) {
            String intent = entry.getKey();
            List<Pattern> patterns = entry.getValue();

            for (Pattern pattern : patterns) {
                if (pattern.matcher(originalInput.toLowerCase()).find()) {
                    intentScores.merge(intent, 2.0, Double::sum);
                }
            }
        }

        // Score based on entity presence
        Map<String, List<String>> allEntities = combineEntities(sentences);
        if (!allEntities.get("contractNumbers").isEmpty()) {
            intentScores.merge("CONTRACT_DETAILS", 1.5, Double::sum);
        }
        if (!allEntities.get("customerNames").isEmpty()) {
            intentScores.merge("CUSTOMER_INFO", 1.5, Double::sum);
        }
        if (!allEntities.get("accountNumbers").isEmpty()) {
            intentScores.merge("ACCOUNT_INFO", 1.5, Double::sum);
        }

        // Find best intent
        return intentScores.entrySet()
                           .stream()
                           .max(Map.Entry.comparingByValue())
                           .map(Map.Entry::getKey)
                           .orElse("GENERAL_INQUIRY");
    }

    /**
     * Combine entities from all processed sentences
     */
    private Map<String, List<String>> combineEntities(List<ProcessedSentence> sentences) {
        Map<String, List<String>> combinedEntities = new HashMap<>();

        // Initialize entity lists
        combinedEntities.put("contractNumbers", new ArrayList<>());
        combinedEntities.put("customerNames", new ArrayList<>());
        combinedEntities.put("organizationNames", new ArrayList<>());
        combinedEntities.put("locations", new ArrayList<>());
        combinedEntities.put("actions", new ArrayList<>());
        combinedEntities.put("accountNumbers", new ArrayList<>());
        combinedEntities.put("userNames", new ArrayList<>());
        combinedEntities.put("dates", new ArrayList<>());
        combinedEntities.put("phoneNumbers", new ArrayList<>());
        combinedEntities.put("emails", new ArrayList<>());

        // Combine entities from all sentences
        for (ProcessedSentence sentence : sentences) {
            Map<String, List<String>> sentenceEntities = sentence.getEntities();

            for (Map.Entry<String, List<String>> entry : sentenceEntities.entrySet()) {
                String entityType = entry.getKey();
                List<String> entities = entry.getValue();

                List<String> combinedList = combinedEntities.get(entityType);
                if (combinedList != null) {
                    for (String entity : entities) {
                        if (!combinedList.contains(entity)) {
                            combinedList.add(entity);
                        }
                    }
                }
            }
        }

        return combinedEntities;
    }

    /**
     * Calculate confidence score for the parsed query
     */
    private double calculateConfidence(List<ProcessedSentence> sentences, String intent, String originalInput) {
        double confidence = 0.0;

        // Base confidence from intent keywords
        List<String> intentWords = intentKeywords.get(intent);
        if (intentWords != null) {
            long matchCount = sentences.stream()
                                       .flatMap(s -> Arrays.stream(s.getTokens()))
                                       .map(String::toLowerCase)
                                       .filter(intentWords::contains)
                                       .count();

            confidence += Math.min(matchCount * 0.2, 0.6);
        }

        // Confidence from pattern matching
        List<Pattern> patterns = intentPatterns.get(intent);
        if (patterns != null) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(originalInput.toLowerCase()).find()) {
                    confidence += 0.3;
                    break;
                }
            }
        }

        // Confidence from entity extraction
        Map<String, List<String>> entities = combineEntities(sentences);
        long entityCount = entities.values()
                                   .stream()
                                   .mapToLong(List::size)
                                   .sum();
        confidence += Math.min(entityCount * 0.1, 0.3);

        // Confidence from sentence completeness
        if (sentences.size() > 0 && !originalInput.trim().isEmpty()) {
            confidence += 0.1;
        }

        // Apply intent-specific thresholds
        Double threshold = intentConfidenceThresholds.get(intent);
        if (threshold != null && confidence < threshold) {
            confidence = Math.max(confidence, MIN_CONFIDENCE_THRESHOLD);
        }

        return Math.min(confidence, 1.0);
    }

    /**
     * Validate and refine detected intent
     */
    private String validateAndRefineIntent(String intent, Map<String, List<String>> entities, double confidence) {
        // If confidence is too low, default to general inquiry
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            return "GENERAL_INQUIRY";
        }

        // Refine intent based on entity presence
        if ("GENERAL_INQUIRY".equals(intent)) {
            if (!entities.get("contractNumbers").isEmpty()) {
                return "CONTRACT_DETAILS";
            }
            if (!entities.get("customerNames").isEmpty()) {
                return "CUSTOMER_INFO";
            }
            if (!entities.get("accountNumbers").isEmpty()) {
                return "ACCOUNT_INFO";
            }
        }
        if (!entities.get("contractNumbers").isEmpty() && ("GENERAL_INQUIRY".equals(intent) || confidence < 0.5)) {
            return "CONTRACT_DETAILS";
        }

        return intent;
    }

    /**
     * Clean and validate extracted entities
     */
    private Map<String, List<String>> cleanAndValidateEntities(Map<String, List<String>> entities) {
        Map<String, List<String>> cleanedEntities = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
            String entityType = entry.getKey();
            List<String> entityList = entry.getValue();

            List<String> cleanedList = entityList.stream()
                                                 .filter(Objects::nonNull)
                                                 .map(String::trim)
                                                 .filter(s -> !s.isEmpty())
                                                 .distinct()
                                                 .collect(Collectors.toList());

            cleanedEntities.put(entityType, cleanedList);
        }

        return cleanedEntities;
    }

    /**
     * Create comprehensive ParsedQuery object
     */
    private ParsedQuery createParsedQuery(String intent, double confidence, Map<String, List<String>> entities,
                                          String processedInput, String originalInput) {
        ParsedQuery query = new ParsedQuery();
        query.setIntent(intent);
        query.setConfidence(confidence);
        query.setEntities(entities);
        query.setProcessedInput(processedInput);
        query.setOriginalInput(originalInput);
        query.setTimestamp(System.currentTimeMillis());
        query.setProcessingId(UUID.randomUUID().toString());

        return query;
    }

    /**
     * Enrich parsed query with additional metadata
     */
    private void enrichParsedQuery(ParsedQuery query, List<ProcessedSentence> sentences) {
        // Add sentence count
        query.addMetadata("sentenceCount", sentences.size());

        // Add token count
        int totalTokens = sentences.stream()
                                   .mapToInt(s -> s.getTokens().length)
                                   .sum();
        query.addMetadata("tokenCount", totalTokens);

        // Add key phrases
        List<String> allKeyPhrases = sentences.stream()
                                              .flatMap(s -> s.getKeyPhrases().stream())
                                              .distinct()
                                              .collect(Collectors.toList());
        query.addMetadata("keyPhrases", allKeyPhrases);

        // Add processing statistics
        query.addMetadata("cacheHitRate", calculateCacheHitRate());
        query.addMetadata("totalQueriesProcessed", totalQueries.get());
    }

    /**
     * Validation methods
     */
    private boolean isValidContractNumber(String number) {
        return number != null && number.matches("\\d{6,8}") && !number.equals("000000");
    }

    private boolean isValidAccountNumber(String number) {
        return number != null && number.matches("\\d{8,12}") && !number.equals("00000000");
    }

    private boolean isValidUserName(String userName) {
        return userName != null && userName.matches("[a-zA-Z0-9_]{3,20}");
    }

    private boolean isValidPersonName(String name) {
        return name != null && name.matches("[A-Za-z\\s]{2,50}") &&
               !Arrays.asList("user", "customer", "client", "person").contains(name.toLowerCase());
    }

    private boolean isValidKeyPhrase(String phrase) {
        return phrase != null && phrase.length() > 2 && phrase.length() < 50 &&
               !stopWords.contains(phrase.toLowerCase());
    }

    /**
     * Fallback person name extraction
     */
    private List<String> extractPersonNamesFallback(String[] tokens) {
        List<String> names = new ArrayList<>();

        for (int i = 0; i < tokens.length - 1; i++) {
            String token1 = tokens[i];
            String token2 = tokens[i + 1];

            if (Character.isUpperCase(token1.charAt(0)) && Character.isUpperCase(token2.charAt(0)) &&
                token1.matches("[A-Za-z]+") && token2.matches("[A-Za-z]+")) {

                String fullName = token1 + " " + token2;
                if (isValidPersonName(fullName)) {
                    names.add(fullName);
                }
            }
        }

        return names;
    }

    /**
     * Cache management methods
     */
    private String generateCacheKey(String input) {
        return Integer.toString(input.toLowerCase()
                                     .trim()
                                     .hashCode());
    }

    private void cacheQuery(String key, ParsedQuery result) {
        if (queryCache.size() >= MAX_CACHE_SIZE) {
            // Simple LRU eviction - remove oldest entries
            Iterator<String> iterator = queryCache.keySet().iterator();
            for (int i = 0; i < MAX_CACHE_SIZE / 4 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }
        queryCache.put(key, result);
    }

    private double calculateCacheHitRate() {
        long total = totalQueries.get();
        long hits = cacheHits.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Error handling
     */
    private ParsedQuery createErrorResponse(String errorMessage, String originalInput) {
        ParsedQuery errorQuery = new ParsedQuery();
        errorQuery.setIntent("ERROR");
        errorQuery.setConfidence(0.0);
        errorQuery.setOriginalInput(originalInput);
        errorQuery.setProcessedInput("");
        errorQuery.setEntities(new HashMap<>());
        errorQuery.setTimestamp(System.currentTimeMillis());
        errorQuery.addMetadata("error", errorMessage);

        return errorQuery;
    }

    /**
     * Configuration initialization methods
     */
    private Map<String, Double> initializeConfidenceThresholds() {
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("CONTRACT_DETAILS", 0.3);
        thresholds.put("CUSTOMER_INFO", 0.25);
        thresholds.put("ACCOUNT_INFO", 0.25);
        thresholds.put("USER_MANAGEMENT", 0.4);
        thresholds.put("SYSTEM_STATUS", 0.35);
        thresholds.put("GENERAL_INQUIRY", 0.1);
        thresholds.put("GREETING", 0.2);
        thresholds.put("GOODBYE", 0.2);
        return thresholds;
    }

    private Map<String, List<String>> initializeIntentKeywords() {
        Map<String, List<String>> keywords = new HashMap<>();

        keywords.put("CONTRACT_DETAILS",
                     Arrays.asList("contract", "agreement", "deal", "policy", "details", "information", "show", "get",
                                   "find", "lookup", "retrieve", "view", "display", "give", "tell", "about", "data",
                                   "say", "check", "need", "want"));

        keywords.put("CUSTOMER_INFO",
                     Arrays.asList("customer", "client", "person", "user", "account", "profile", "information",
                                   "details", "data", "record", "history"));

        keywords.put("ACCOUNT_INFO",
                     Arrays.asList("account", "balance", "statement", "transaction", "payment", "billing", "invoice",
                                   "charges", "fees", "summary"));

        keywords.put("USER_MANAGEMENT",
                     Arrays.asList("user", "username", "password", "login", "access", "permission", "role", "admin",
                                   "create", "delete", "modify", "update"));

        keywords.put("SYSTEM_STATUS",
                     Arrays.asList("status", "health", "system", "server", "database", "connection", "online",
                                   "offline", "working", "down", "error", "issue"));

        keywords.put("GENERAL_INQUIRY",
                     Arrays.asList("help", "what", "how", "when", "where", "why", "can", "could", "would", "should",
                                   "tell", "explain", "describe"));

        keywords.put("GREETING",
                     Arrays.asList("hello", "hi", "hey", "good", "morning", "afternoon", "evening", "greetings",
                                   "welcome", "start", "begin"));

        keywords.put("GOODBYE",
                     Arrays.asList("bye", "goodbye", "farewell", "exit", "quit", "end", "finish", "thanks", "thank",
                                   "done", "complete"));

        return keywords;
    }

    private Map<String, String> initializeSpellingCorrections() {
        Map<String, String> corrections = new HashMap<>();
        corrections.put("contarct", "contract");
        corrections.put("contrct", "contract");
        corrections.put("conract", "contract");
        corrections.put("cntrct", "contract");
        corrections.put("contrac", "contract");
        corrections.put("contractt", "contract");
        corrections.put("custmer", "customer");
        corrections.put("cusotmer", "customer");
        corrections.put("accont", "account");
        corrections.put("acount", "account");
        corrections.put("infomation", "information");
        corrections.put("informaton", "information");
        corrections.put("detials", "details");
        corrections.put("deatils", "details");
        corrections.put("shwo", "show");
        corrections.put("teh", "the");
        corrections.put("recrod", "record");
        corrections.put("reocrd", "record");
        return corrections;
    }

    private Map<String, List<Pattern>> initializeIntentPatterns() {
        Map<String, List<Pattern>> patterns = new HashMap<>();

        patterns.put("CONTRACT_DETAILS",
                     Arrays.asList(Pattern.compile("(?i).*contract\\s+\\d+.*"),
                                   Pattern.compile("(?i).*show.*contract.*"),
                                   Pattern.compile("(?i).*get.*contract.*details.*"),
                                   Pattern.compile("(?i).*find.*agreement.*"),
                                   Pattern.compile("(?i).*lookup.*policy.*"),
                                   Pattern.compile("(?i).*can\\s+you\\s+give.*contract.*"),
                                   Pattern.compile("(?i).*could\\s+you\\s+tell.*contract.*"),
                                   Pattern.compile("(?i).*do\\s+you\\s+have.*contract.*"),
                                   Pattern.compile("(?i).*what\\s+does\\s+contract.*say.*"),
                                   Pattern.compile("(?i).*tell\\s+me\\s+about\\s+contract.*"),
                                   Pattern.compile("(?i).*i\\s+need.*check.*contract.*"),
                                   Pattern.compile("(?i).*i\\s+want.*details.*contract.*"),
                                   Pattern.compile("(?i).*need\\s+data.*contract.*"),
                                   Pattern.compile("(?i).*please\\s+show\\s+contract.*")));

        patterns.put("CUSTOMER_INFO",
                     Arrays.asList(Pattern.compile("(?i).*customer\\s+\\w+.*"),
                                   Pattern.compile("(?i).*client\\s+information.*"),
                                   Pattern.compile("(?i).*user\\s+profile.*"),
                                   Pattern.compile("(?i).*account\\s+holder.*")));

        patterns.put("ACCOUNT_INFO",
                     Arrays.asList(Pattern.compile("(?i).*account\\s+\\d+.*"), Pattern.compile("(?i).*balance.*"),
                                   Pattern.compile("(?i).*statement.*"),
                                   Pattern.compile("(?i).*transaction.*history.*")));

        patterns.put("USER_MANAGEMENT",
                     Arrays.asList(Pattern.compile("(?i).*create\\s+user.*"), Pattern.compile("(?i).*delete\\s+user.*"),
                                   Pattern.compile("(?i).*user\\s+access.*"),
                                   Pattern.compile("(?i).*reset\\s+password.*")));

        patterns.put("GREETING",
                     Arrays.asList(Pattern.compile("(?i)^(hi|hello|hey|good\\s+(morning|afternoon|evening)).*"),
                                   Pattern.compile("(?i).*greetings.*"), Pattern.compile("(?i).*welcome.*")));

        patterns.put("GOODBYE",
                     Arrays.asList(Pattern.compile("(?i).*(bye|goodbye|farewell|exit|quit).*"),
                                   Pattern.compile("(?i).*thank.*you.*"),
                                   Pattern.compile("(?i).*(done|finished|complete).*")));

        return patterns;
    }


    private Set<String> initializeStopWords() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList("a", "an", "and", "are", "as", "at", "be", "by",
                                                                       "for", "from", "has", "he", "in", "is", "it",
                                                                       "its", "of", "on", "that", "the", "to", "was",
                                                                       "will", "with", "i", "me", "my", "we", "our",
                                                                       "you", "your", "this", "these", "those", "they",
                                                                       "them", "their", "have", "had", "do", "does",
                                                                       "did", "can", "could", "should", "would", "may",
                                                                       "might", "must", "shall", "will", "am", "is",
                                                                       "are", "was", "were", "been", "being", "have",
                                                                       "has", "had", "do", "does", "did")));
    }

    /**
     * Public utility methods for external access
     */
    public Map<String, Object> getServiceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueries", totalQueries.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheHitRate", calculateCacheHitRate());
        stats.put("cacheSize", queryCache.size());
        stats.put("averageProcessingTimes", getAverageProcessingTimes());
        stats.put("modelsLoaded", getLoadedModelsCount());
        return stats;
    }

    private Map<String, Long> getAverageProcessingTimes() {
        return new HashMap<>(processingTimes);
    }

    private int getLoadedModelsCount() {
        int count = 0;
        if (tokenizer != null)
            count++;
        if (posTagger != null)
            count++;
        if (sentenceDetector != null)
            count++;
        if (personNameFinder != null)
            count++;
        if (organizationNameFinder != null)
            count++;
        if (locationNameFinder != null)
            count++;
        if (chunker != null)
            count++;
        if (lemmatizer != null)
            count++;
        return count;
    }

    public void clearCache() {
        queryCache.clear();
        LOGGER.info("Query cache cleared");
    }

    public void resetStatistics() {
        totalQueries.set(0);
        cacheHits.set(0);
        processingTimes.clear();
        LOGGER.info("Service statistics reset");
    }

    /**
     * Cleanup method for proper resource management
     */
    public void cleanup() {
        if (personNameFinder != null) {
            personNameFinder.clearAdaptiveData();
        }
        if (organizationNameFinder != null) {
            organizationNameFinder.clearAdaptiveData();
        }
        if (locationNameFinder != null) {
            locationNameFinder.clearAdaptiveData();
        }

        queryCache.clear();
        processingTimes.clear();

        LOGGER.info("ChatbotNLPService cleanup completed");
    }

    /**
     * Inner class to represent processed sentence data
     */
    private static class ProcessedSentence {
        private final String[] tokens;
        private final String[] posTags;
        private final String[] lemmas;
        private final String[] chunks;
        private final Map<String, List<String>> entities;
        private final List<String> keyPhrases;
        private final String originalSentence;

        public ProcessedSentence(String[] tokens, String[] posTags, String[] lemmas, String[] chunks,
                                 Map<String, List<String>> entities, List<String> keyPhrases, String originalSentence) {
            this.tokens = tokens;
            this.posTags = posTags;
            this.lemmas = lemmas;
            this.chunks = chunks;
            this.entities = entities;
            this.keyPhrases = keyPhrases;
            this.originalSentence = originalSentence;
        }

        // Getters
        public String[] getTokens() {
            return tokens;
        }

        public String[] getPosTags() {
            return posTags;
        }

        public String[] getLemmas() {
            return lemmas;
        }

        public String[] getChunks() {
            return chunks;
        }

        public Map<String, List<String>> getEntities() {
            return entities;
        }

        public List<String> getKeyPhrases() {
            return keyPhrases;
        }

        public String getOriginalSentence() {
            return originalSentence;
        }
    }
}


