package com.espressotech.monetization

internal object AdRegionDetector {
    private val russianTimeZones = setOf(
        "Europe/Kaliningrad", "Europe/Moscow", "Europe/Simferopol", "Europe/Kirov",
        "Europe/Volgograd", "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk",
        "Asia/Barnaul", "Asia/Tomsk", "Asia/Novokuznetsk", "Asia/Krasnoyarsk",
        "Asia/Irkutsk", "Asia/Chita", "Asia/Yakutsk", "Asia/Khandyga",
        "Asia/Vladivostok", "Asia/Ust-Nera", "Asia/Magadan", "Asia/Sakhalin",
        "Asia/Srednekolymsk", "Asia/Kamchatka", "Asia/Anadyr",
    )

    fun isRussianMarket(region: String, timezone: String): Boolean =
        region.equals("RU", ignoreCase = true) || timezone in russianTimeZones
}
