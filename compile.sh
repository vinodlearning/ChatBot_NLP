#!/bin/bash

echo "=== Compiling Simple Java Spell Checker ==="

# Compile Java source (no external dependencies needed)
echo "Compiling SpellChecker.java..."
javac SpellChecker.java

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Use './run.sh' to run the spell checker"
else
    echo "Compilation failed!"
    exit 1
fi