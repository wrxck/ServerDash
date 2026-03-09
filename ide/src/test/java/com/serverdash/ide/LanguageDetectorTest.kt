package com.serverdash.ide

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageDetectorTest {
    @Test
    fun `detects kotlin files`() {
        assertThat(LanguageDetector.detect("Main.kt")).isEqualTo("kotlin")
    }

    @Test
    fun `detects javascript files`() {
        assertThat(LanguageDetector.detect("index.js")).isEqualTo("javascript")
    }

    @Test
    fun `detects typescript files`() {
        assertThat(LanguageDetector.detect("app.tsx")).isEqualTo("typescriptreact")
    }

    @Test
    fun `detects dockerfile`() {
        assertThat(LanguageDetector.detect("Dockerfile")).isEqualTo("dockerfile")
    }

    @Test
    fun `detects yaml files`() {
        assertThat(LanguageDetector.detect("docker-compose.yml")).isEqualTo("yaml")
    }

    @Test
    fun `returns plaintext for unknown`() {
        assertThat(LanguageDetector.detect("mystery.xyz")).isEqualTo("plaintext")
    }

    @Test
    fun `handles dotfiles`() {
        assertThat(LanguageDetector.detect(".gitignore")).isEqualTo("plaintext")
    }

    @Test
    fun `handles case insensitive extensions`() {
        assertThat(LanguageDetector.detect("README.MD")).isEqualTo("markdown")
    }
}
