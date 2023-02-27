package com.leesfamily.chuno.network.data

import android.graphics.Point

data class Room(
    var id: Long,

    var title: String,

    var password: String?,

    var isPublic: Boolean,

    var maxPlayers: Int = 10,

    var currentPlayers: Int,

    var radius: Int,

    var distance: Double,

    var host: User,

    var dateTime: DateTime,

    var lat: Double,

    var lng: Double,

    var pushed: Boolean
)
