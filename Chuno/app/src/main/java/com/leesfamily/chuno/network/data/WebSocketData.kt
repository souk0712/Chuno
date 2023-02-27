package com.leesfamily.chuno.network.data

data class AllRoomList(
    var type: String,
    var roomInfo: ArrayList<CurRoomInfo>
)

data class CurRoomInfo(
    var roomid: Long,
    var playercnt: Int
)

data class WaitingInfo(
    var type:String,
    var present:Int,
    var players:List<Player>
)
