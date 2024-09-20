package com.leesfamily.chuno.start

import android.content.ActivityNotFoundException
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentInputInfoBinding
import com.leesfamily.chuno.network.login.LoginGetter
import com.leesfamily.chuno.util.custom.MyCustomDialog
import com.leesfamily.chuno.util.custom.MyCustomDialogInterface
import com.leesfamily.chuno.util.login.LoginPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class InputInfoFragment : Fragment(), MyCustomDialogInterface {
    private lateinit var binding: FragmentInputInfoBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var photoResultLauncher: ActivityResultLauncher<Intent>
    private val inputInfoViewModel: InputInfoViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var flag = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentInputInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbarInclude.toolbarTitle.text = getString(R.string.profile_text)

        mainViewModel.email.observe(viewLifecycleOwner) { email ->
            inputInfoViewModel.setEmail(email)
            Log.d(TAG, "onViewCreated: email $email")
        }
        binding.profile.setOnClickListener {
            startDialog()
        }
        binding.saveButton.setOnClickListener {
            when (flag) {
                2 -> {  // 가능
                    val phoneLength = binding.editNumber.text.length
                    if (phoneLength in 10..11) {
                        inputInfoViewModel.setNickName(binding.editNick.text.toString())
                        inputInfoViewModel.setPhone(binding.editNumber.text.toString())
                        inputInfoViewModel.register { findNavController().navigate(R.id.homeFragment) }
                        inputInfoViewModel.registerSuccess.observe(viewLifecycleOwner) {
                            if (it) {
                                Toast.makeText(context, "환영합니다!", Toast.LENGTH_SHORT).show()

                            } else {
                                Toast.makeText(context, "회원 가입 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        flag = 4
                        showCustomDialog()
                    }
                }
                else -> {
                    showCustomDialog()
                }
            }
        }

        val imm = context?.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?

        binding.editNick.apply {
            requestFocus()
            imm!!.showSoftInput(this, 0)

            addTextChangedListener(
                object : TextWatcher {
                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun afterTextChanged(arg0: Editable) {
                        // 입력이 끝났을 때 조치
                        val count = arg0.length
                        Log.d(TAG, "afterTextChanged: arg0 $arg0")
                        if (count in 1..6) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val result = LoginGetter().requestNickDuplic(arg0.toString())
                                if (!result) {
                                    // 중복
                                    flag = 1
                                    Log.d(TAG, "afterTextChanged: flag $flag")
                                } else {
                                    flag = 2

                                }
                                setLimitText(flag)
                            }
                            // 가능
                        } else if (count > 6) {
                            // 초과
                            flag = 3
                        } else {
                            // 0
                            flag = 0
                        }

                        if (flag == 0) {
                            binding.limitText.visibility = View.GONE
                        } else {
                            binding.limitText.visibility = View.VISIBLE
                            setLimitText(flag)
                        }
                    }
                }
            )
        }
    }

    private fun showCustomDialog() {
        MyCustomDialog(requireContext(), this).apply {
            when (flag) {
                0 -> {  // 입력안함
                    message = getString(R.string.nick_no_input_message)
                }
                1 -> {  // 중복
                    message = getString(R.string.nick_overlap_message)
                }
                3 -> {  //초과
                    message = getString(R.string.nick_limit_message)
                }
                4 -> {    // 전화번호 미입력
                    message = getString(R.string.phone_no_input_message)
                }
            }
            yesMsg = getString(R.string.ok)
            show()
        }
    }

    private fun init() {
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == AppCompatActivity.RESULT_OK) {
                    val imageUri = it.data?.data ?: return@registerForActivityResult
                    setImgUri(imageUri)
                }
            }
        photoResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == AppCompatActivity.RESULT_OK) {
                    val extras = it.data?.extras
                    val bitmap = extras?.get("data") as Bitmap?
                    binding.profile.setImageBitmap(bitmap)
                    if (bitmap != null) {
                        inputInfoViewModel.setImage(bitmap, context?.cacheDir.toString())
                    }
                }
            }
    }

    private fun setLimitText(flag: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.limitText.apply {
                when (flag) {
                    1 -> {
                        // 중복
                        setText(R.string.nick_overlap_message)
                        setTextColor(Color.RED)
                    }
                    2 -> {
                        // 가능
                        setText(R.string.nick_allow_message)
                        setTextColor(Color.GREEN)
                    }
                    3 -> {
                        // 글자 수 초과
                        setText(R.string.nick_limit_message)
                        setTextColor(Color.RED)
                    }
                }
            }
        }
    }

    private fun ableButton(flag: Boolean) {
        binding.saveButton.apply {
            when (flag) {
                true -> {
                    isClickable = true
                    backgroundTintList =
                        ColorStateList.valueOf(requireActivity().getColor(R.color.primary))
                    Log.d(TAG, "ableButton: true")
                }
                false -> {
                    isClickable = false
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B9B7BD"))
                    Log.d(TAG, "ableButton: false")
                }
            }

        }
    }

    private fun setImgUri(imgUri: Uri) {
        imgUri.let {
            val bitmap: Bitmap
            if (Build.VERSION.SDK_INT < 28) {
                bitmap = MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    imgUri
                )
                binding.profile.setImageBitmap(bitmap)
            } else {
                val source =
                    ImageDecoder.createSource(requireContext().contentResolver, imgUri)
                bitmap = ImageDecoder.decodeBitmap(source)
                binding.profile.setImageBitmap(bitmap)
                inputInfoViewModel.setImage(bitmap, context?.cacheDir.toString())
            }
        }
    }

    private fun startDialog() {
        val options = arrayOf<CharSequence>(
            "사진", "앨범"
        )
        val builder: AlertDialog.Builder = AlertDialog.Builder(
            requireContext()
        )

        builder.apply {
            setTitle("사진/앨범")

            setItems(
                options,
                DialogInterface.OnClickListener { dialog, which ->
                    if (options[which] == "사진") {
                        try {
                            val cameraIntent = Intent(
                                MediaStore.ACTION_IMAGE_CAPTURE
                            )
                            photoResultLauncher.launch(cameraIntent)
                        } catch (ex: ActivityNotFoundException) {
                            val errorMessage =
                                "이미지를 캡쳐할 수 없습니다. 다시 시도해주세요."
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } else if (options[which] == "앨범") {
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            activityResultLauncher.launch(
                                Intent.createChooser(
                                    this,
                                    "Get Album"
                                )
                            )
                        }
                    }
                })
            create()
            show()
        }
    }

    companion object {
        private const val TAG = "추노_InputInfo"
    }

    override fun onYesButtonClicked() {}

    override fun onNoButtonClicked() {}
}