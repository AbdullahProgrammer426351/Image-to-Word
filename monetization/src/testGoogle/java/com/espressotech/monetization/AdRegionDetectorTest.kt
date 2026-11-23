package com.espressotech.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRegionDetectorTest {
    @Test
    fun russianRegionSelectsYandex() {
        assertTrue(AdRegionDetector.isRussianMarket("RU", "Europe/London"))
    }

    @Test
    fun russianTimezoneSelectsYandex() {
        assertTrue(AdRegionDetector.isRussianMarket("GB", "Asia/Vladivostok"))
    }

    @Test
    fun nonRussianSignalsSelectGoogleMediation() {
        assertFalse(AdRegionDetector.isRussianMarket("GB", "Europe/London"))
    }
}
