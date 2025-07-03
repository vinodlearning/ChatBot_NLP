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
            "program", "question", "number", "public", "information", "development"
        };
        
        for (String word : commonWords) {
            dictionary.put(word, 1000);
        }
        
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
            // Remove punctuation for spell checking
            String cleanWord = word.replaceAll("[^a-zA-Z]", "");
            if (cleanWord.isEmpty()) {
                correctedText.append(word).append(" ");
                continue;
            }
            
            try {
                List<Suggestion> suggestions = findSuggestions(cleanWord);
                
                if (!suggestions.isEmpty() && suggestions.get(0).distance > 0) {
                    // Replace with best suggestion, preserving original punctuation
                    String corrected = word.replace(cleanWord, suggestions.get(0).word);
                    correctedText.append(corrected);
                    System.out.println("Corrected: " + word + " -> " + corrected);
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