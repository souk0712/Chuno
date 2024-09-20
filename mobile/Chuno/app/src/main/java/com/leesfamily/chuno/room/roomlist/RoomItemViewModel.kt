package com.leesfamily.chuno.room.roomlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.leesfamily.chuno.BuildConfig
import com.leesfamily.chuno.network.data.Chat
import com.leesfamily.chuno.network.data.CurRoomInfo
import com.leesfamily.chuno.network.data.Player
import com.leesfamily.chuno.network.data.Room
import com.leesfamily.chuno.network.room.RoomGetter
import com.leesfamily.chuno.network.websocket.MessageListener
import com.leesfamily.chuno.network.websocket.WebSocketManager
import com.leesfamily.chuno.util.ListLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class RoomItemViewModel : ViewModel() {
    // 변경가능한 Mutable 타입의 LiveData
    private var _roomData: MutableLiveData<Room> = MutableLiveData()
    private var _players: ListLiveData<Player> = ListLiveData()
    private var _chatList: ListLiveData<Chat> = ListLiveData()
    private var _roomList: ListLiveData<Room> = ListLiveData()
    private var _curRoomList: ListLiveData<CurRoomInfo> = ListLiveData()
    private var _isFinish: MutableLiveData<Boolean> = MutableLiveData()

    // 무결성을 위한 Getter
    val roomData: LiveData<Room>
        get() = _roomData

    val players: ListLiveData<Player>
        get() = _players

    val chatList: ListLiveData<Chat>
        get() = _chatList

    val roomList: ListLiveData<Room>
        get() = _roomList

    val curRoomList: ListLiveData<CurRoomInfo>
        get() = _curRoomList

    val isFinish: MutableLiveData<Boolean>
        get() = _isFinish


    // setter
    fun updateRoomData(newRoomData: Room) {
        _roomData.value = newRoomData
        Log.d(TAG, "updateRoomData: newRoomData ${newRoomData}")
    }

    fun setRoomList(token: String, curRoomList: ArrayList<CurRoomInfo>) {
        _isFinish.value = false
        Log.d(TAG, "setRoomList: 시작 $curRoomList")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "viewModelScope: 시작")
            val temp = arrayListOf<Room>()
            curRoomList.forEachIndexed { index, room ->
                Log.d(TAG, "setRoomList: index : $index, room: $room")
                RoomGetter().requestRoomData(token, room.roomid)?.let {
                    temp.add(it)
                }
            }
            _roomList.postValue(temp)
            _isFinish.postValue(true)
            Log.d(TAG, "setRoomList: isFinish? ${isFinish.value}")
        }
    }

    fun setCurRoomInfoList(curRoomInfoList: ArrayList<CurRoomInfo>) {
        Log.d(TAG, "setCurRoomInfoList: ")
        _curRoomList.postValue(curRoomInfoList)
        Log.d(TAG, "setCurRoomInfoList: ${curRoomList.value}")
    }

    fun setPlayers(user: ArrayList<Player>) {
        _players.value = user
        Log.d(TAG, "user: ${user}")
        Log.d(TAG, "addPlayer: ${_players.value}")
    }


    fun removePlayer(player: Player) {
        _players.remove(player)
        Log.d(TAG, "removePlayer: ${_players.value}")
    }

    fun addPlayer(user: Player) {
        _players.value?.add(user)
        Log.d(TAG, "user: ${user}")
        Log.d(TAG, "addPlayer: ${_players.value}")
    }

    fun clearPlayer() {
        _players.clear(true)
    }

    fun getPlayer(index: Int) {
        _players.value?.get(index)
    }

    fun addChat(chat: Chat) {
        _chatList.value?.add(chat)
        Log.d(TAG, "user: $chat")
        Log.d(TAG, "addPlayer: ${_chatList.value}")
    }

    fun clearChat() {
        _chatList.clear(true)
    }

    fun initWebSocketManager(_messageListener: MessageListener) {
        WebSocketManager.init(BuildConfig.SOCKET_URL, _messageListener)
    }

    fun getWebSocketAllRoomList() {
        val result = WebSocketManager.getAllRoom()
        Log.d(TAG, "sendMessage: $result")
    }

    fun connectWebSocket() {
        thread {
            kotlin.run {
                WebSocketManager.connect()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: RoomItemViewModel")
    }

    companion object {
        private const val TAG = "추노_RoomItemViewModel"
    }
}