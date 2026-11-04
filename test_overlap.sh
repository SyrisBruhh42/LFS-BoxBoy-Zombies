#!/bin/bash
# If entity is at [90, 110] and wall is at [100, 150].
# overlapLeft (pushing entity out left) should be -10.
# wallMinX = 100
# wallMaxX = 150
# entMinX = 90
# entMaxX = 110
# My code:
# overlapLeft = entMaxX - wallMinX = 110 - 100 = 10
# overlapRight = wallMaxX - entMinX = 150 - 90 = 60
# minOverlapX = if (10 < 60) -10 else 60 => -10
# So MTV is -10. Correct! Pushing entity left by 10.
