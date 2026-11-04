#!/bin/bash
for i in {1..30}; do
  ./gradlew testDebugUnitTest --tests "*WindowBarricadeTier2SteelTest*" --no-configuration-cache
  if [ $? -eq 0 ]; then
    echo "Tests passed!"
    break
  else
    echo "Failed, retrying..."
    sleep 30
  fi
done
