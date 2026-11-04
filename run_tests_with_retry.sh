#!/bin/bash
MAX_RETRIES=5
DELAY=10

for ((i=1; i<=MAX_RETRIES; i++)); do
    echo "Running tests, attempt $i of $MAX_RETRIES..."
    if ./gradlew testDebugUnitTest --tests "com.example.game.combat.modules.VoltaicScreamerZombieTest" --no-configuration-cache; then
        echo "Tests passed!"
        break
    else
        echo "Tests failed or hit rate limit."
        if [[ $i -lt $MAX_RETRIES ]]; then
            echo "Waiting $DELAY seconds before retrying..."
            sleep $DELAY
        fi
    fi
done
