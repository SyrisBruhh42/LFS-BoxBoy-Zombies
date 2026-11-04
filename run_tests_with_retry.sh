#!/bin/bash
MAX_RETRIES=3
RETRY_DELAY=15
count=0

while [ $count -lt $MAX_RETRIES ]; do
    ./gradlew testDebugUnitTest --tests "com.example.game.combat.modules.ShamblerHordeBehaviorTest" --no-configuration-cache
    if [ $? -eq 0 ]; then
        echo "Tests passed!"
        break
    fi
    echo "Tests failed, retrying in $RETRY_DELAY seconds..."
    sleep $RETRY_DELAY
    count=$((count+1))
done

if [ $count -eq $MAX_RETRIES ]; then
    echo "Failed after $MAX_RETRIES retries."
fi
