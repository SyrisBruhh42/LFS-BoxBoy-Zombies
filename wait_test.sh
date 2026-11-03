#!/bin/bash
for i in {1..30}; do
  ./gradlew testDebugUnitTest --tests "com.example.game.ai.director.modules.OllamaVendingStockSynthesizerTest" --no-configuration-cache
  if [ $? -eq 0 ]; then
    break
  fi
  sleep 10
done
