#!/bin/bash
MAX_RETRIES=10
DELAY=15
for i in $(seq 1 $MAX_RETRIES); do
  echo "Attempt $i..."
  ./gradlew testDebugUnitTest --tests "com.example.game.combat.modules.HellSprinterFlankBehaviorTest" --no-configuration-cache
  if [ $? -eq 0 ]; then
    echo "Tests passed!"
    break
  fi
  echo "Tests failed, sleeping for $DELAY seconds before retrying..."
  sleep $DELAY
done
