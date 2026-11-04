#!/bin/bash
git checkout app/build.gradle.kts
sed -i 's/testImplementation(libs.robolectric)/\/\/testImplementation(libs.robolectric)/g' app/build.gradle.kts

# Keep files we are removing to revert later
rm app/src/test/java/com/example/ExampleRobolectricTest.kt
rm app/src/test/java/com/example/NetworkAndEnvelopeIntegrationTest.kt
rm app/src/test/java/com/example/base/FirestoreEmulatorTestBase.kt
rm app/src/test/java/com/example/game/ai/director/modules/EmergencySupplyDropDirectorTest.kt
rm app/src/test/java/com/example/data/BoxBoyRepositoryRuleTest.kt
rm app/src/test/java/com/example/game/ai/director/modules/FrontierPrefetchCoordinatorTest.kt
rm app/src/test/java/com/example/game/world/elona/vaults/ElonaRedwoodRailyardChokepointAlleyVaultTest.kt
rm app/src/test/java/com/example/game/world/elona/vaults/ElonaRedwoodRailyardVendingRotundaVaultTest.kt
rm app/src/test/java/com/example/game/world/elona/vaults/ElonaSunvaleBazaarChokepointAlleyVaultTest.kt
rm app/src/test/java/com/example/game/world/elona/vaults/ElonaSunvaleBazaarPackAPunchSanctumVaultTest.kt

./gradlew testDebugUnitTest --tests com.example.game.loot.borderlands.catalog.NovaBlastShieldGeneratorTest --no-configuration-cache --offline

if [ $? -eq 0 ]; then
  echo "Tests passed!"
else
  echo "Tests failed"
fi

git checkout app/build.gradle.kts
git checkout app/src/test/java/com/example/ExampleRobolectricTest.kt
git checkout app/src/test/java/com/example/NetworkAndEnvelopeIntegrationTest.kt
git checkout app/src/test/java/com/example/base/FirestoreEmulatorTestBase.kt
git checkout app/src/test/java/com/example/game/ai/director/modules/EmergencySupplyDropDirectorTest.kt
git checkout app/src/test/java/com/example/data/BoxBoyRepositoryRuleTest.kt
git checkout app/src/test/java/com/example/game/ai/director/modules/FrontierPrefetchCoordinatorTest.kt
git checkout app/src/test/java/com/example/game/world/elona/vaults/ElonaRedwoodRailyardChokepointAlleyVaultTest.kt
git checkout app/src/test/java/com/example/game/world/elona/vaults/ElonaRedwoodRailyardVendingRotundaVaultTest.kt
git checkout app/src/test/java/com/example/game/world/elona/vaults/ElonaSunvaleBazaarChokepointAlleyVaultTest.kt
git checkout app/src/test/java/com/example/game/world/elona/vaults/ElonaSunvaleBazaarPackAPunchSanctumVaultTest.kt
