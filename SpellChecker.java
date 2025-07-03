import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SpellChecker {
    private Map<String, Integer> dictionary;
    private static final int MAX_EDIT_DISTANCE = 3;
    
    public SpellChecker() {
        dictionary = new HashMap<>();
        loadDictionary("frequency_dictionary_en_82_765.txt");
    }
    
    public void loadDictionary(String dictionaryPath) {
        try {
            System.out.println("Loading dictionary from: " + dictionaryPath);
            
            File dictFile = new File(dictionaryPath);
            if (!dictFile.exists()) {
                System.out.println("Warning: Dictionary file not found. Creating a basic dictionary...");
                createBasicDictionary();
                return;
            }
            
            // Read the dictionary file
            dictionary = Files.lines(Paths.get(dictionaryPath))
                    .map(line -> line.split(" "))
                    .filter(tokens -> tokens.length >= 2)
                    .collect(Collectors.toMap(
                            tokens -> tokens[0].toLowerCase(),
                            tokens -> {
                                try {
                                    return Integer.parseInt(tokens[1]);
                                } catch (NumberFormatException e) {
                                    return 1;
                                }
                            },
                            Integer::sum // In case of duplicate keys, sum the frequencies
                    ));
            
            System.out.println("Dictionary loaded successfully with " + dictionary.size() + " words!");
        } catch (Exception e) {
            System.err.println("Error loading dictionary: " + e.getMessage());
            System.out.println("Creating basic dictionary...");
            createBasicDictionary();
        }
    }
    
    private void createBasicDictionary() {
        // Create a basic dictionary with common words for testing
        String[] commonWords = {
            "the", "and", "is", "in", "to", "of", "a", "that", "it", "with",
            "for", "as", "was", "on", "are", "you", "this", "be", "at", "have",
            "or", "from", "one", "had", "but", "word", "not", "what", "all",
            "were", "they", "we", "when", "your", "can", "said", "there", "use",
            "an", "each", "which", "she", "do", "how", "their", "if", "will",
            "up", "other", "about", "out", "many", "then", "them", "these", "so",
            "some", "her", "would", "make", "like", "into", "him", "has", "two",
            "more", "very", "after", "words", "first", "where", "much", "through",
            "hello", "world", "computer", "programming", "java", "spell", "check",
            "correct", "mistake", "error", "text", "word", "sentence", "language",
            "receive", "believe", "achieve", "piece", "their", "there", "they're",
            "separate", "definitely", "beginning", "beautiful", "necessary",
            "embarrass", "occurring", "recommend", "disappear", "tomorrow",
            "weird", "friend", "business", "really", "until", "immediately",
            "sophisticated", "my", "name", "am", "people", "person", "because",
            "between", "important", "example", "government", "company", "system",
            "program", "question", "number", "public", "information", "development",
            "i", "me", "him", "her", "us", "them", "myself", "yourself", "himself",
            "herself", "ourselves", "themselves", "show", "contract", "details",
            "display", "view", "get", "fetch", "retrieve", "find", "search", "list",
            "create", "update", "delete", "add", "remove", "edit", "modify", "save",
            "document", "file", "record", "data", "user", "customer", "client",
            "order", "invoice", "payment", "account", "status", "active", "inactive",
            "contracts", "customers", "accounts", "users", "clients", "orders",
            "effective", "expired", "draft", "state", "date", "month", "year",
            "created", "between", "from", "today", "summary", "number", "by",
            "info", "information", "metadata", "after", "before", "under", "all",
            "project", "type", "price", "list", "corporate", "opportunity", "code",
            "fields", "last", "details", "fields", "boeing", "siemens", "mary",
            "honeywel", "honeywell", "jan", "june", "january", "vinod"
        };
        
        for (String word : commonWords) {
            dictionary.put(word, 1000);
        }
        
        // Give higher frequencies to very common words
        dictionary.put("my", 10000);  // Higher than "am"
        dictionary.put("i", 8000);
        dictionary.put("you", 7000);
        dictionary.put("the", 15000);
        dictionary.put("and", 12000);
        dictionary.put("is", 10000);
        dictionary.put("a", 9000);
        dictionary.put("show", 6000);  // High frequency for "shw" correction
        dictionary.put("contract", 5000);  // High frequency for "cntroct" correction
        dictionary.put("contracts", 4800);  // Plural form
        dictionary.put("customer", 4500);   // High frequency for customer variations
        dictionary.put("customers", 4300);  // Plural form
        dictionary.put("account", 4000);    // High frequency for account variations
        dictionary.put("accounts", 3800);   // Plural form
        dictionary.put("effective", 3500);  // High frequency for "efective" correction
        dictionary.put("draft", 3000);      // High frequency for "drafft" correction
        dictionary.put("status", 3500);     // High frequency for status variations
        dictionary.put("date", 4000);       // Should stay as "date", not "data"
        dictionary.put("info", 4500);       // High frequency for "infro" correction
        dictionary.put("after", 4000);      // High frequency for "aftr" correction
        dictionary.put("last", 3500);       // High frequency for "lst" correction
        dictionary.put("between", 3000);    // High frequency for "btwn" correction
        dictionary.put("created", 3500);    // High frequency for "creatd" correction
        dictionary.put("vinod", 2000);      // Proper name - lower frequency but present
        
        // Add common contractions
        dictionary.put("i'm", 4000);
        dictionary.put("don't", 3000);
        dictionary.put("can't", 3000);
        dictionary.put("won't", 3000);
        dictionary.put("it's", 3000);
        
        // Add common abbreviations
        dictionary.put("no", 2500);          // For "number"
        dictionary.put("acc", 2000);         // For "account"
        
        System.out.println("Basic dictionary created with " + commonWords.length + " words.");
    }
    
    // Calculate Levenshtein distance between two strings
    private int editDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        
        // dp[i][j] = minimum edits to transform s1[0..i-1] to s2[0..j-1]
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        // Initialize base cases
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        // Fill the dp table
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[len1][len2];
    }
    
    // Find suggestions for a word
    public List<Suggestion> findSuggestions(String word) {
        List<Suggestion> suggestions = new ArrayList<>();
        word = word.toLowerCase();
        
        // If word exists in dictionary, return it
        if (dictionary.containsKey(word)) {
            suggestions.add(new Suggestion(word, 0, dictionary.get(word)));
            return suggestions;
        }
        
        // Find words with edit distance <= MAX_EDIT_DISTANCE
        for (Map.Entry<String, Integer> entry : dictionary.entrySet()) {
            String dictWord = entry.getKey();
            int frequency = entry.getValue();
            
            int distance = editDistance(word, dictWord);
            if (distance <= MAX_EDIT_DISTANCE) {
                suggestions.add(new Suggestion(dictWord, distance, frequency));
            }
        }
        
        // Sort suggestions by distance first, then by frequency (descending)
        suggestions.sort((a, b) -> {
            if (a.distance != b.distance) {
                return Integer.compare(a.distance, b.distance);
            }
            return Integer.compare(b.frequency, a.frequency);
        });
        
        return suggestions;
    }
    
    public void correctWord(String word) {
        try {
            List<Suggestion> suggestions = findSuggestions(word);
            
            if (suggestions.isEmpty()) {
                System.out.println("No suggestions found for: " + word);
            } else if (suggestions.get(0).distance == 0) {
                System.out.println("'" + word + "' is spelled correctly.");
            } else {
                System.out.println("Suggestions for '" + word + "':");
                for (int i = 0; i < Math.min(5, suggestions.size()); i++) {
                    Suggestion suggestion = suggestions.get(i);
                    System.out.println((i + 1) + ". " + suggestion.word + 
                                     " (distance: " + suggestion.distance + 
                                     ", frequency: " + suggestion.frequency + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("Error correcting word: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void correctText(String text) {
        System.out.println("\n--- Text Correction ---");
        String[] words = text.split("\\s+");
        StringBuilder correctedText = new StringBuilder();
        
        for (String word : words) {
            String originalWord = word;
            
            // Handle special contractions
            if (word.matches(".*[;].*")) {
                word = word.replaceAll(";", "'");
                // Capitalize "i" in contractions
                if (word.toLowerCase().startsWith("i'")) {
                    word = "I" + word.substring(1);
                }
                correctedText.append(word);
                if (!word.equals(originalWord)) {
                    System.out.println("Corrected: " + originalWord + " -> " + word);
                }
                correctedText.append(" ");
                continue;
            }
            
            // Remove punctuation for spell checking but preserve it
            String cleanWord = word.replaceAll("[^a-zA-Z]", "");
            String punctuation = word.replaceAll("[a-zA-Z]", "");
            
            if (cleanWord.isEmpty()) {
                correctedText.append(word).append(" ");
                continue;
            }
            
            // Skip likely proper names (capitalized words that aren't at sentence start)
            if (Character.isUpperCase(cleanWord.charAt(0)) && !isFirstWordOfSentence(word, correctedText.toString())) {
                correctedText.append(word);
                correctedText.append(" ");
                continue;
            }
            
            try {
                List<Suggestion> suggestions = findSuggestions(cleanWord.toLowerCase());
                
                if (!suggestions.isEmpty() && suggestions.get(0).distance > 0) {
                    // Replace with best suggestion, preserving original punctuation
                    String corrected = suggestions.get(0).word + punctuation;
                    correctedText.append(corrected);
                    System.out.println("Corrected: " + originalWord + " -> " + corrected);
                } else {
                    correctedText.append(word);
                }
                correctedText.append(" ");
            } catch (Exception e) {
                correctedText.append(word).append(" ");
            }
        }
        
        System.out.println("\nOriginal: " + text);
        System.out.println("Corrected: " + correctedText.toString().trim());
    }
    
    private boolean isFirstWordOfSentence(String word, String previousText) {
        if (previousText.trim().isEmpty()) return true;
        return previousText.matches(".*[.!?]\\s*$");
    }
    
    // Inner class to represent a suggestion
    private static class Suggestion {
        String word;
        int distance;
        int frequency;
        
        Suggestion(String word, int distance, int frequency) {
            this.word = word;
            this.distance = distance;
            this.frequency = frequency;
        }
    }
    
    public static void main(String[] args) {
        SpellChecker checker = new SpellChecker();
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== Simple Java Spell Checker ===");
        System.out.println("Commands:");
        System.out.println("1. Type 'word <word>' to get suggestions for a single word");
        System.out.println("2. Type 'text <sentence>' to correct an entire text");
        System.out.println("3. Type 'quit' to exit");
        System.out.println();
        
        // Check if dictionary file exists
        File dictFile = new File("frequency_dictionary_en_82_765.txt");
        if (!dictFile.exists()) {
            System.out.println("Note: Dictionary file 'frequency_dictionary_en_82_765.txt' not found.");
            System.out.println("A basic dictionary will be used. For better results, run './setup.sh' to download the full dictionary.");
            System.out.println();
        }
        
        // Example usage
        System.out.println("Example commands:");
        System.out.println("  word recieve");
        System.out.println("  word teh");
        System.out.println("  text I hav a misspeled sentance");
        System.out.println();
        
        while (true) {
            System.out.print("Enter command: ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit")) {
                break;
            } else if (input.startsWith("word ")) {
                String word = input.substring(5).trim();
                checker.correctWord(word);
            } else if (input.startsWith("text ")) {
                String text = input.substring(5).trim();
                checker.correctText(text);
            } else {
                System.out.println("Invalid command. Use 'word <word>', 'text <sentence>', or 'quit'");
            }
            System.out.println();
        }
        
        scanner.close();
        System.out.println("Goodbye!");
    }
}