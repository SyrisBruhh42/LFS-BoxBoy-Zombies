#!/bin/bash
for i in {1..10}; do
  ./gradlew testDebugUnitTest --tests "com.example.game.combat.modules.CryoBarrelFreezeNovaTest" --no-configuration-cache
  if [ $? -eq 0 ]; then
    echo "Tests passed!"
    break
  else
    echo "Test failed, retrying in 10 seconds..."
    sleep 10
  fi
done
