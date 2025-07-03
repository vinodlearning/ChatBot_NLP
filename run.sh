#!/bin/bash

echo "=== Running Simple Java Spell Checker ==="

# Check if class file exists
if [ ! -f "SpellChecker.class" ]; then
    echo "Error: SpellChecker.class not found"
    echo "Please run './compile.sh' first to compile the program"
    exit 1
fi

# Run the spell checker (no external dependencies needed)
java SpellChecker