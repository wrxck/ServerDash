package com.serverdash.ide

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MonacoBridgeTest {
    @Test
    fun `cursor change updates state`() = runTest {
        val bridge = MonacoBridge()
        bridge.cursorPosition.test {
            assertThat(awaitItem()).isEqualTo(1 to 1)
            bridge.onCursorChange(10, 25)
            assertThat(awaitItem()).isEqualTo(10 to 25)
        }
    }

    @Test
    fun `content change marks dirty`() = runTest {
        val bridge = MonacoBridge()
        bridge.isDirty.test {
            assertThat(awaitItem()).isFalse()
            bridge.onContentChanged("modified")
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `reset dirty clears flag`() = runTest {
        val bridge = MonacoBridge()
        bridge.isDirty.test {
            assertThat(awaitItem()).isFalse()
            bridge.onContentChanged("modified")
            assertThat(awaitItem()).isTrue()
            bridge.resetDirty()
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `onReady emits true`() = runTest {
        val bridge = MonacoBridge()
        bridge.isReady.test {
            assertThat(awaitItem()).isFalse()
            bridge.onReady()
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `onSave triggers save callback`() = runTest {
        val bridge = MonacoBridge()
        var saveCalled = false
        bridge.onSaveRequested = { saveCalled = true }
        bridge.onSave()
        assertThat(saveCalled).isTrue()
    }
}
