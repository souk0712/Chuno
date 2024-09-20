package com.leesfamily.chuno.game.wait

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.leesfamily.chuno.BuildConfig
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentWaitingRoomListBinding
import com.leesfamily.chuno.game.GameViewModel
import com.leesfamily.chuno.network.data.AllRoomList
import com.leesfamily.chuno.network.data.Room
import com.leesfamily.chuno.network.data.WaitingInfo
import com.leesfamily.chuno.network.websocket.MessageListener
import com.leesfamily.chuno.network.websocket.WebSocketListener
import com.leesfamily.chuno.network.websocket.WebSocketManager
import com.leesfamily.chuno.room.roomlist.RoomItemViewModel
import com.leesfamily.chuno.room.roomlist.RoomListFragment
import com.leesfamily.chuno.util.custom.CreateRoomDialog1
import com.leesfamily.chuno.util.custom.CreateRoomDialog2
import com.leesfamily.chuno.util.custom.CreateRoomDialogInterface

/**
 * A fragment representing a list of Items.
 */
class WaitingRoomFragment : Fragment(), MessageListener {
    private lateinit var binding: FragmentWaitingRoomListBinding
    private var columnCount = 3
    private val roomItemViewModel: RoomItemViewModel by activityViewModels()
    private val gameViewModel: GameViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var roomData: Room
    private lateinit var dialog1: CreateRoomDialog1
    private lateinit var dialog2: CreateRoomDialog2
    private lateinit var APPLICATION_SERVER_URL: String

    //    private lateinit var httpClient: CustomHttpClient
//    private lateinit var session: SessionWaiting

    private lateinit var callback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
        APPLICATION_SERVER_URL = BuildConfig.SERVER_URL
        roomItemViewModel.initWebSocketManager(this)
        roomItemViewModel.connectWebSocket()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWaitingRoomListBinding.inflate(inflater, container, false)
        roomItemViewModel.roomData.value?.let {
            roomData = it
            gameViewModel.updateRoomData(it)
        }

        roomItemViewModel.roomData.observe(viewLifecycleOwner) {
            binding.toolbarInclude.toolbarTitle.text = it.title
        }

        binding.toolbarInclude.toolbar.inflateMenu(R.menu.menu_toolbar)

        roomItemViewModel.players.observe(viewLifecycleOwner) {
            binding.userList.apply {
                layoutManager =
                    GridLayoutManager(context, columnCount)
                adapter = UserItemRecyclerViewAdapter(it)
            }
        }

        roomItemViewModel.chatList.observe(viewLifecycleOwner) {
            binding.chatList.apply {
                layoutManager =
                    LinearLayoutManager(context)
                adapter = ChatItemRecyclerViewAdapter(it)
            }
        }

        binding.footer.sendButton.setOnClickListener {
            binding.userList
        }

        binding.footer.readyButton.setOnClickListener {
            findNavController().navigate(R.id.action_waitingRoomFragment_to_game_view)
        }

//        httpClient = CustomHttpClient(APPLICATION_SERVER_URL)
//        val sessionId = roomData.id.toString()
//        getToken(sessionId)
//        Log.d(TAG, "onCreateView: sessionId $sessionId")
        return binding.root
    }
/*

    fun addChat(chat: Chat) {
        viewModel.addChat(chat)
        binding.userList.adapter?.notifyDataSetChanged()
    }

    fun removePlayer(player: Player) {
        viewModel.removePlayer(player)
        binding.userList.adapter?.notifyDataSetChanged()
    }

    // OpenVidu 토큰을 요청
    // sessionId : 토큰을 원하는 OpenVidu 세션
    private fun getToken(sessionId: String) {
        try {
            // Session Request
            val sessionBody = "{\"customSessionId\": \"$sessionId\"}"
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            httpClient.httpCall(
                "/api/sessions",
                "POST",
                "application/json",
                sessionBody,
                object : Callback {
                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "responseString: " + response.body.string())

                        // Token Request
                        val tokenBody =
                            "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        httpClient.httpCall(
                            "/api/sessions/$sessionId/connections",
                            "POST",
                            "application/json",
                            tokenBody,
                            object : Callback {
                                override fun onResponse(call: Call, response: Response) {
                                    var responseString: String? = null
                                    try {
                                        responseString = response.body.string()
                                    } catch (e: IOException) {
                                        Log.e(TAG, "Error getting body", e)
                                    }
                                    if (responseString != null) {
                                        getTokenSuccess(responseString, sessionId)
                                    }
                                }

                                override fun onFailure(call: Call, e: IOException) {
                                    Log.e(
                                        TAG,
                                        "Error POST /api/sessions/SESSION_ID/connections",
                                        e
                                    )
                                    connectionError(APPLICATION_SERVER_URL)
                                }
                            })
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Error POST /api/sessions", e)
                        connectionError(APPLICATION_SERVER_URL)
                    }
                })
        } catch (e: IOException) {
            Log.e(TAG, "Error getting token", e)
            e.printStackTrace()
            connectionError(APPLICATION_SERVER_URL)
        }
    }

    // 토큰을 얻으면 Session, LocalParticipant를 생성, 카메라를 캡쳐한다.
    private fun getTokenSuccess(token: String, sessionId: String) {
        // Initialize our session
        session = SessionWaiting(
            sessionId,
            token,
            binding.userList,
            this
        )
        Log.d(TAG, "getTokenSuccess: ")

        val me = mainViewModel.user.value!!
        val localParticipant =
            LocalParticipantWaiting(
                me.nickname.toString(),
                me.level.toString(),
                false,
                session,
                requireContext(),
            )
        localParticipant.startCamera()

        activity?.runOnUiThread {
//            viewModel.addPlayer(Player(me.nickname!!, me.level.toString(), false))
            binding.userList.adapter?.notifyItemChanged(0)
            Log.d(
                TAG,
                "getTokenSuccess:mainViewModel.getUser().value ${mainViewModel.user.value}"
            )
            Log.d(TAG, "getTokenSuccess:viewModel.players.value ${viewModel.players.value}")
        }

        // Initialize and connect the websocket to OpenVidu Server
        startWebSocket()
    }

    fun createRemoteParticipantVideo(remoteParticipant: RemoteParticipantWaiting) {
        val mainHandler: Handler = Handler(requireContext().mainLooper)
        val myRunnable = Runnable {
//            viewModel.addPlayer(
//                Player(
//                    remoteParticipant.participantName,
//                    remoteParticipant.participantLevel, false
//                )
//            )
            binding.userList.adapter?.notifyDataSetChanged()
            Log.d(TAG, "createRemoteParticipantVideo: players : ${viewModel.players} ")
        }
        mainHandler.post(myRunnable)
    }

    fun leaveSession() {
        session.leaveSession()
        viewModel.clearPlayer()
        Log.d(TAG, "leaveSession: waitingRoomFragment")
        httpClient.dispose()
    }

    private fun startWebSocket() {
        val webSocket = CustomWebSocketWaiting(session, this)
        Log.d(TAG, "startWebSocket: $webSocket 시작")
        webSocket.execute()
        Log.d(TAG, "startWebSocket: $webSocket 끝")
        session.setWebSocket(webSocket)
    }

    private fun connectionError(url: String?) {
        val myRunnable = Runnable {
            val toast: Toast =
                Toast.makeText(context, "Error connecting to $url", Toast.LENGTH_LONG)
            toast.show()
        }
        Handler(requireContext().mainLooper).post(myRunnable)
    }
*/

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.room_info -> {
                val childFragmentManager = childFragmentManager
                childFragmentManager.findFragmentByTag("createRoomDialog1")
                    ?.let {
                        childFragmentManager.beginTransaction().remove(it)
                    }
                dialog1 = CreateRoomDialog1(requireContext(), object : CreateRoomDialogInterface {
                    override fun onNextButtonClicked(view: View) {
                        childFragmentManager.findFragmentByTag("createRoomDialog1")?.let {
                            childFragmentManager.beginTransaction().remove(it)
                        }
                        dialog2 = CreateRoomDialog2(requireContext(), this).apply {
                            show(childFragmentManager, "createRoomDialog2")
                        }
                    }

                    override fun onPrevButtonClicked(view: View) {
                        dialog1.show(childFragmentManager, "createRoomDialog1")
                    }

                }).apply {
                    title = roomData.title
                    password = roomData.password
                    reservationDate = roomData.dateTime.dateToString()
                    reservationHour = roomData.dateTime.hour
                    reservationMin = roomData.dateTime.minute
                    curValue = roomData.maxPlayers
                    isReadOnly = true
                    show(childFragmentManager, "infoRoomDialog1")
                }
                roomItemViewModel.roomData.observe(viewLifecycleOwner) {
                }

                true
            }
            R.id.notify_off -> {
                true
            }
            R.id.notify_on -> {
                true
            }
            R.id.out -> {
//                leaveSession()
                findNavController().navigate(R.id.homeFragment)
                true
            }
            else -> super.onContextItemSelected(item)
        }

    }

    override fun onDestroy() {
        roomItemViewModel.clearPlayer()
//        leaveSession()
        Log.d(TAG, "onDestroy: ")
        super.onDestroy()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "onAttach: ")
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
//                leaveSession()
                Log.d(TAG, "handleOnBackPressed: ")
                roomItemViewModel.clearPlayer()
                findNavController().navigate(R.id.homeFragment)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onDetach() {
        Log.d(TAG, "onDetach: ")
        super.onDetach()
        callback.remove()
    }

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"
        private const val TAG = "추노_WaitingRoomFragment"

        @JvmStatic
        fun newInstance(columnCount: Int) =
            WaitingRoomFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                }
            }
    }

    override fun onConnectSuccess() {
        gameViewModel.enterRoom(
            roomData.id.toString()
        )
    }

    override fun onConnectFailed() {

    }

    override fun onClose() {

    }

    override fun onMessage(text: String?) {
        Log.d(TAG, "onMessage: text $text")
        val json = Gson().fromJson(text, WaitingInfo::class.java)
        when (json.type) {
            "me" -> {
                Log.d(TAG, "onMessage: me ")
            }
            "leave" -> {
                Log.d(TAG, "onMessage: leave")
            }
            "chat" -> {
                Log.d(TAG, "onMessage: chat")
            }
            "already"->{
                Log.d(TAG, "onMessage: already")
                Log.d(TAG, "onMessage: ${json.players}, ${json.present}")
            }
        }
    }
}