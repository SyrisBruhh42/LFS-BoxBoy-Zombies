package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    org.junit.Assume.assumeTrue(
      "Skip Robolectric Maven download in constrained cloud VMs",
      System.getenv("JULES_CLOUD_SKIP_ROBOLECTRIC") != "1"
    )
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(context.packageName == "com.example" || context.packageName == "com.lewisfamilysystems.boxboyzombies")
    val appName = context.getString(R.string.app_name)
    assertEquals("BoxBoy Zombies", appName)
  }
}
