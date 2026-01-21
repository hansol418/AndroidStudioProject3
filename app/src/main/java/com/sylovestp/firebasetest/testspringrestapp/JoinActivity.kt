package com.sylovestp.firebasetest.testspringrestapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.sylovestp.firebasetest.testspringrestapp.databinding.ActivityJoinBinding
import com.sylovestp.firebasetest.testspringrestapp.dto.UserDTO
import com.sylovestp.firebasetest.testspringrestapp.retrofit.MyApplication
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class JoinActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var binding: ActivityJoinBinding
    private var imageUri: Uri? = null

    private lateinit var addressFinderLauncher: ActivityResultLauncher<Bundle>
    private val selectImageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            imageUri = result.data?.data
            Glide.with(this)
                .load(imageUri)
                .apply(RequestOptions().circleCrop())
                .into(imageView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageView = binding.userProfile

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.userProfile.setOnClickListener { openGallery() }
        binding.joinBtn.setOnClickListener { registerUser() }
        binding.loginBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        addressFinderLauncher = registerForActivityResult(AddressFinder.contract) { result ->
            if (result != Bundle.EMPTY) {
                val address = result.getString(AddressFinder.ADDRESS)
                val zipCode = result.getString(AddressFinder.ZIPCODE)
                val editableText: Editable =
                    Editable.Factory.getInstance().newEditable("[$zipCode] $address")
                binding.userAddress.text = editableText
            }
        }

        binding.findAddressBtn.setOnClickListener { addressFinderLauncher.launch(Bundle()) }
    }

    private fun registerUser() {
        val username = binding.userUsername.text.toString()
        val name = binding.userName.text.toString()
        val password = binding.userPassword1.text.toString()
        val email = binding.userEmail.text.toString()
        val phone = binding.userPhone.text.toString()
        val address = binding.userAddress.text.toString()
        val detailAddress = binding.userAddressDetail.text.toString()
        val fullAddress = "$address $detailAddress"
        val userDTO = UserDTO(username, name, password, email, phone, fullAddress)

        if (username.isNotBlank() && password.isNotBlank() && email.isNotBlank()) {
            imageUri?.let { uri -> processImage(userDTO, uri) }
                ?: run { uploadData(createRequestBodyFromDTO(userDTO), null) }
        } else {
            Toast.makeText(this, "모든 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadData(user: RequestBody, profileImage: MultipartBody.Part?) {
        val networkService = (applicationContext as MyApplication).networkService
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = networkService.registerUser(user, profileImage).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@JoinActivity, "회원가입 성공", Toast.LENGTH_SHORT).show()
                        navigateToMainActivity()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("JoinActivity", "회원가입 실패: ${response.code()} $errorBody")
                        Toast.makeText(
                            this@JoinActivity,
                            "회원가입 실패: ${response.code()} - ${errorBody ?: "알 수 없는 오류"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("JoinActivity", "네트워크 오류: ${e.message}")
                    Toast.makeText(this@JoinActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun createRequestBodyFromDTO(userDTO: UserDTO): RequestBody {
        val gson = Gson()
        val json = gson.toJson(userDTO)
        return json.toRequestBody("application/json".toMediaTypeOrNull())
    }

    private fun createMultipartBodyFromBytes(imageBytes: ByteArray): MultipartBody.Part {
        val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), imageBytes)
        return MultipartBody.Part.createFormData("profileImage", "image.jpg", requestFile)
    }

    private fun openGallery() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {

            Intent(MediaStore.ACTION_PICK_IMAGES).apply {

            }
        } else {

            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"

            }
        }
        selectImageLauncher.launch(intent)
    }

    private fun processImage(userDTO: UserDTO, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resizedBitmap = getResizedBitmap(uri, 200, 200)
                val imageBytes = bitmapToByteArray(resizedBitmap)
                val profileImagePart = createMultipartBodyFromBytes(imageBytes)
                uploadData(createRequestBodyFromDTO(userDTO), profileImagePart)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("JoinActivity", "이미지 처리 실패: ${e.message}")
                    Toast.makeText(this@JoinActivity, "이미지 처리 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getResizedBitmap(uri: Uri, width: Int, height: Int): Bitmap {
        return withContext(Dispatchers.IO) {
            val futureTarget: FutureTarget<Bitmap> = Glide.with(this@JoinActivity)
                .asBitmap()
                .load(uri)
                .override(width, height)
                .submit()
            futureTarget.get()
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }
}
