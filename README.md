# Simple Java Spell Checker

A standalone Java program that provides spell checking and correction using a simple edit distance algorithm. This implementation requires **no external dependencies** - just pure Java!

## Features

- Fast spell checking using Levenshtein edit distance algorithm
- Single word correction with multiple suggestions
- Full text correction
- Interactive command-line interface
- No external dependencies or Maven required
- Works with custom dictionary files or built-in basic dictionary

## Quick Start

1. **Setup** (downloads dictionary - optional):
   ```bash
   chmod +x setup.sh compile.sh run.sh
   ./setup.sh
   ```

2. **Compile**:
   ```bash
   ./compile.sh
   ```

3. **Run**:
   ```bash
   ./run.sh
   ```

## Usage

Once running, you can use these commands:

### Single Word Correction
```
word hello
word teh
word recieve
```

### Text Correction
```
text I hav a misspeled sentance
text The quik brown fox jumps
```

### Exit
```
quit
```

## Example Session

```
=== Simple Java Spell Checker ===
Commands:
1. Type 'word <word>' to get suggestions for a single word
2. Type 'text <sentence>' to correct an entire text
3. Type 'quit' to exit

Enter command: word recieve
Suggestions for 'recieve':
1. receive (distance: 2, frequency: 1000)

Enter command: word teh
Suggestions for 'teh':
1. the (distance: 1, frequency: 1000)

Enter command: text I hav a misspeled sentance
--- Text Correction ---
Corrected: hav -> have
Corrected: misspeled -> misspelled
Corrected: sentance -> sentence

Original: I hav a misspeled sentance
Corrected: I have a misspelled sentence
```

## Project Structure

```
├── SpellChecker.java           # Main spell checker class (standalone)
├── setup.sh                    # Downloads dictionary file (optional)
├── compile.sh                  # Compiles the Java program
├── run.sh                     # Runs the compiled program
├── README.md                  # This file
└── frequency_dictionary_en_82_765.txt # English dictionary (optional)
```

## Manual Setup (Alternative)

If the automatic setup doesn't work, you can manually:

1. **Download dictionary** (optional):
   - Go to: https://raw.githubusercontent.com/wolfgarbe/SymSpell/master/SymSpell.FrequencyDictionary/frequency_dictionary_en_82_765.txt
   - Save as: `frequency_dictionary_en_82_765.txt`

2. **Compile manually**:
   ```bash
   javac SpellChecker.java
   ```

3. **Run manually**:
   ```bash
   java SpellChecker
   ```

## Configuration

You can modify these settings in `SpellChecker.java`:

- `MAX_EDIT_DISTANCE`: Maximum edit distance for suggestions (default: 2)
- Dictionary: The program works with either a full dictionary file or a built-in basic dictionary

## Algorithm

This spell checker uses the **Levenshtein distance algorithm** to find the edit distance between words. The Levenshtein distance is the minimum number of single-character edits (insertions, deletions, or substitutions) required to change one word into another.

### How it works:
1. Load dictionary from file (or use built-in basic dictionary)
2. For each misspelled word, calculate edit distance to all dictionary words
3. Return suggestions sorted by edit distance and word frequency
4. For text correction, apply best suggestion while preserving punctuation

## Requirements

- Java 8 or higher
- Internet connection (only for initial dictionary download, optional)
- `wget` (for automatic dictionary download, or manual download if not available)

## Notes

- The program includes a built-in basic dictionary with common words
- Works without internet connection using the basic dictionary
- Download the full dictionary file (82,765 words) for better results
- The dictionary file is about 6MB and includes word frequencies
- Zero external dependencies - pure Java implementation
- Edit distance algorithm is optimized for reasonable performance