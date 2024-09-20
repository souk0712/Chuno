package com.leesfamily.chuno.game

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.leesfamily.chuno.network.data.Room
import com.leesfamily.chuno.network.data.User
import com.leesfamily.chuno.network.websocket.WebSocketManager

class GameViewModel : ViewModel() {
    private var _roomData: MutableLiveData<Room> = MutableLiveData()
    private var _currentPosition: MutableLiveData<LatLng> = MutableLiveData()
    private var _chaserList: MutableLiveData<List<User>> = MutableLiveData()
    private var _runnerList: MutableLiveData<List<User>> = MutableLiveData()
//    private var _user: MutableLiveData<User> = MutableLiveData()

    val roomData: LiveData<Room>
        get() = _roomData

    val currentPosition: LiveData<LatLng>
        get() = _currentPosition

    val chaserList: LiveData<List<User>>
        get() = _chaserList

    val runnerList: LiveData<List<User>>
        get() = _runnerList

//    val user: LiveData<User>
//        get() = _user

    // setter
    fun updateRoomData(newRoomData: Room) {
        _roomData.value = newRoomData
        Log.d("추노_GameViewModel", "updateRoomData: $newRoomData")
    }

    // setter
    fun updateRunnerList(runnerList: List<User>) {
        _runnerList.value = runnerList
    }

    // setter
    fun updateChaserList(chaserList: List<User>) {
        _chaserList.value = chaserList
    }

    fun enterRoom(
        roomId: String
    ) {
        WebSocketManager.enterRoom(
            roomId
        )
    }
}