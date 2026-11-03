#!/bin/bash
for i in {1..5}; do
  ./gradlew testDebugUnitTest --tests "com.example.game.ai.director.modules.OllamaVendingStockSynthesizerTest" --no-configuration-cache && break || sleep 5
done
