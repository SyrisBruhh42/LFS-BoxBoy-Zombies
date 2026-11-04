#!/bin/bash
sed -i 's/val minOverlapX = if (overlapLeft < overlapRight) -overlapLeft else overlapRight/val minOverlapX = if (overlapLeft < overlapRight) -overlapLeft else overlapRight/g' app/src/main/java/com/example/game/economy/vendors/PlaceableBarricadeWallBlock.kt
