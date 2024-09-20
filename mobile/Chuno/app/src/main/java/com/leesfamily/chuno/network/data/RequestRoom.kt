package com.leesfamily.chuno.network.data

import android.graphics.Point

data class RequestRoom(
    var title: String,

    var password: String?,

    var isPublic: Boolean,

    var isToday: Boolean,

    var lat: Double,

    var lng: Double,

    var maxPlayers: Int,

    var radius: Int,

    var hour: Int,

    var minute: Int,
)
