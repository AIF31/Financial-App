package com.aif31.pocket

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentionalCiFailureTest {
    @Test fun deliberately_fails_to_verify_the_ci_gate() {
        fail("Intentional failure-propagation exercise for issue 20")
    }
}