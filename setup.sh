#!/bin/bash

echo "=== Simple Java Spell Checker Setup ==="

# Download frequency dictionary (optional)
echo "Downloading English frequency dictionary..."
if [ ! -f "frequency_dictionary_en_82_765.txt" ]; then
    # Try multiple sources for the dictionary
    echo "Trying to download dictionary from GitHub..."
    curl -o "frequency_dictionary_en_82_765.txt" "https://raw.githubusercontent.com/wolfgarbe/SymSpell/master/SymSpell.FrequencyDictionary/frequency_dictionary_en_82_765.txt" 2>/dev/null
    
    # Check if download was successful (file should be larger than 100 bytes)
    if [ -f "frequency_dictionary_en_82_765.txt" ] && [ $(wc -c < frequency_dictionary_en_82_765.txt) -gt 100 ]; then
        echo "Dictionary downloaded successfully!"
    else
        echo "Dictionary download failed or returned an error."
        rm -f frequency_dictionary_en_82_765.txt 2>/dev/null
        
        echo ""
        echo "Note: The spell checker will work with a built-in basic dictionary."
        echo "For better results, you can manually download the dictionary:"
        echo "1. Visit: https://github.com/wolfgarbe/SymSpell/tree/master/SymSpell.FrequencyDictionary"
        echo "2. Download: frequency_dictionary_en_82_765.txt"
        echo "3. Place it in this directory"
        echo ""
    fi
else
    echo "Dictionary already exists: frequency_dictionary_en_82_765.txt"
fi

echo "Setup completed!"
echo "The spell checker includes a basic built-in dictionary and will work without external files."
echo ""
echo "Next steps:"
echo "1. Run './compile.sh' to compile the program"
echo "2. Run './run.sh' to start the spell checker"