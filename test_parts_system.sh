#!/bin/bash

echo "=== Testing Parts Management Spell Checker ==="
echo "Testing all parts management queries..."
echo ""

# Read each line from the test file and run it through the spell checker
counter=1
while IFS= read -r line; do
    if [ ! -z "$line" ]; then
        echo "Test $counter: $line"
        echo "text $line" | ./run.sh | grep -E "(Original:|Corrected:)" | tail -2
        echo ""
        ((counter++))
    fi
done < test_parts_queries.txt

echo "=== Test Complete ==="