package com.leesfamily.chuno.room.roomlist

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.leesfamily.chuno.BuildConfig
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentRoomListBinding
import com.leesfamily.chuno.network.data.AllRoomList
import com.leesfamily.chuno.network.room.RoomGetter
import com.leesfamily.chuno.network.websocket.MessageListener
import com.leesfamily.chuno.network.websocket.WebSocketListener
import com.leesfamily.chuno.network.websocket.WebSocketManager
import com.leesfamily.chuno.util.PermissionHelper
import com.leesfamily.chuno.util.custom.CreateRoomDialog1
import com.leesfamily.chuno.util.custom.CreateRoomDialog2
import com.leesfamily.chuno.util.custom.CreateRoomDialogInterface
import com.leesfamily.chuno.util.login.LoginPrefManager
import com.leesfamily.chuno.util.login.UserDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/**
 * 게임을 위한 방의 목록을 보여주는 Fragment이다.
 **/
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class RoomListFragment : Fragment(), CreateRoomDialogInterface, MessageListener {
    lateinit var binding: FragmentRoomListBinding
    private var param1: String? = null
    private var param2: String? = null
    private var columnCount = 1

    private val viewModel: RoomItemViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private lateinit var dialog1: CreateRoomDialog1
    private lateinit var dialog2: CreateRoomDialog2
//    lateinit var updateRoomList: Task<Location>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initWebSocketManager(this)
        viewModel.connectWebSocket()
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRoomListBinding.inflate(inflater, container, false)
        binding.toolbarInclude.toolbarTitle.text = getString(R.string.room_list_title)
        if (mainViewModel.user.value == null) {
            mainViewModel.setUserInfo()
        }
        if (mainViewModel.token.value == null) {
            LoginPrefManager.getLastLoginToken()?.let {
                mainViewModel.setLastToken(it)
                UserDB.setIsConfirmToken(true)
            }
        }

        if (PermissionHelper.hasLocationPermission(requireContext())) {
            getRoomList()
            binding.refreshLayout.apply {
                setOnRefreshListener {
                    // 새로고침 코드를 작성
                    Log.d(TAG, "onCreateView: refreshing recyclerview")
//                    if (updateRoomList.isComplete)
                    getRoomList()
                    // 새로고침 완료시, 새로고침 아이콘이 사라질 수 있게 isRefreshing = false
                    viewModel.isFinish.observe(viewLifecycleOwner) {
                        if (it) {
                            this.isRefreshing = false
                        }
                    }

                }
            }

            val fab: View = binding.createRoom
            fab.setOnClickListener { view ->
                val childFragmentManager = childFragmentManager
                childFragmentManager.findFragmentByTag("createRoomDialog1")
                    ?.let {
                        childFragmentManager.beginTransaction().remove(it)
                    }
                dialog1 = CreateRoomDialog1(requireContext(), this).apply {
                    show(childFragmentManager, "createRoomDialog1")
                }

            }
        }
        return binding.root
    }

    @SuppressLint("MissingPermission")
    fun getRoomList() {
        viewModel.curRoomList.observe(viewLifecycleOwner) {
            if (mainViewModel.token.value != null && it != null) {
                viewModel.setRoomList(mainViewModel.token.value!!, it)
                viewModel.isFinish.observe(viewLifecycleOwner) {
                    binding.allRoomList.apply {
                        layoutManager = when {
                            columnCount <= 1 -> LinearLayoutManager(
                                requireContext()
                            )
                            else -> GridLayoutManager(
                                requireContext(),
                                columnCount
                            )
                        }
                        adapter =
                            RoomItemRecyclerViewAdapter(
                                viewModel.roomList.value ?: listOf(),
                                navigate(),
                                viewModel
                            )
                    }
                    binding.loadingBar.root.visibility = View.GONE
                    binding.refreshLayout.visibility = View.VISIBLE
                    binding.createRoom.visibility = View.VISIBLE
                }
            }
        }


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun navigate(): NavController {
        val navHostFragment =
            activity?.supportFragmentManager?.findFragmentById(R.id.start_nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }

    override fun onNextButtonClicked(view: View) {
        val childFragmentManager = childFragmentManager
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

    override fun onConnectSuccess() {
        viewModel.getWebSocketAllRoomList()
    }

    override fun onConnectFailed() {
    }

    override fun onClose() {
    }

    override fun onMessage(text: String?) {
        val json = Gson().fromJson(text, AllRoomList::class.java)
        Log.d(TAG, "onMessage: roomlistfragment ${json.roomInfo}")
        when (json.type) {
            "rooms" -> {
                Log.d(TAG, "onMessage: rooms!!!!")
                viewModel.setCurRoomInfoList(json.roomInfo)
                Log.d(TAG, "onMessage: all_room ${json.roomInfo}")
            }
            "me" -> {
                Log.d(TAG, "onMessage: me")
            }
            "leave" -> {
                Log.d(TAG, "onMessage: leave")
            }
            "chat" -> {
                Log.d(TAG, "onMessage: chat")
            }
        }
    }

    companion object {
        private const val TAG = "추노_RoomListFragment"
        const val ARG_COLUMN_COUNT = "column-count"

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            RoomListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                    putInt(ARG_COLUMN_COUNT, columnCount)
                }
            }
    }

}