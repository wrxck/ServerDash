package com.serverdash.app.util

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.core.util.formatBytes
import com.serverdash.app.core.util.formatUptime
import org.junit.Test

class DisplayUtilsTest {

    @Test
    fun `formatUptime shows minutes only for short durations`() {
        assertThat(formatUptime(0)).isEqualTo("0m")
        assertThat(formatUptime(59)).isEqualTo("0m")
        assertThat(formatUptime(60)).isEqualTo("1m")
        assertThat(formatUptime(300)).isEqualTo("5m")
        assertThat(formatUptime(3540)).isEqualTo("59m")
    }

    @Test
    fun `formatUptime shows hours and minutes`() {
        assertThat(formatUptime(3600)).isEqualTo("1h 0m")
        assertThat(formatUptime(3660)).isEqualTo("1h 1m")
        assertThat(formatUptime(7200)).isEqualTo("2h 0m")
        assertThat(formatUptime(86399)).isEqualTo("23h 59m")
    }

    @Test
    fun `formatUptime shows days hours minutes`() {
        assertThat(formatUptime(86400)).isEqualTo("1d 0h 0m")
        assertThat(formatUptime(90000)).isEqualTo("1d 1h 0m")
        assertThat(formatUptime(172800)).isEqualTo("2d 0h 0m")
        assertThat(formatUptime(604800)).isEqualTo("7d 0h 0m")
        assertThat(formatUptime(123456)).isEqualTo("1d 10h 17m")
    }

    @Test
    fun `formatBytes for bytes range`() {
        assertThat(formatBytes(0)).isEqualTo("0 B")
        assertThat(formatBytes(500)).isEqualTo("500 B")
        assertThat(formatBytes(1023)).isEqualTo("1023 B")
    }

    @Test
    fun `formatBytes for kilobytes`() {
        assertThat(formatBytes(1024)).isEqualTo("1.0 KB")
        assertThat(formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(formatBytes(1048575)).isEqualTo("1024.0 KB")
    }

    @Test
    fun `formatBytes for megabytes`() {
        assertThat(formatBytes(1_048_576)).isEqualTo("1.0 MB")
        assertThat(formatBytes(10_485_760)).isEqualTo("10.0 MB")
        assertThat(formatBytes(524_288_000)).isEqualTo("500.0 MB")
    }

    @Test
    fun `formatBytes for gigabytes`() {
        assertThat(formatBytes(1_073_741_824)).isEqualTo("1.0 GB")
        assertThat(formatBytes(5_368_709_120)).isEqualTo("5.0 GB")
        assertThat(formatBytes(10_737_418_240)).isEqualTo("10.0 GB")
    }
}
