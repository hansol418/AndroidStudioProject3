package com.sylovestp.firebasetest.testspringrestapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sylovestp.firebasetest.testspringrestapp.dto.PredictionResult
import com.sylovestp.firebasetest.testspringrestapp.retrofit.MyApplication
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AiPredictActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var jwtToken: String

    private val CAMERA_REQUEST_CODE = 100
    private val PICK_MEDIA_REQUEST_CODE = 200
    private val CAMERA_PERMISSION_CODE = 101


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AiPredictActivity", "onCreate 실행됨")
        setContentView(R.layout.activity_ai_predict)

        jwtToken = getJwtTokenFromSharedPreferences()
        if (jwtToken.isEmpty()) {
            showToast("JWT 토큰이 없습니다. 다시 로그인해주세요.")
            finish()
            return
        }

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        val buttonCamera: Button = findViewById(R.id.buttonCamera)
        val buttonGallery: Button = findViewById(R.id.buttonGallery)

        buttonCamera.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }

        buttonGallery.setOnClickListener {

            openSystemPhotoPicker()
        }
    }

    private fun getJwtTokenFromSharedPreferences(): String {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("jwt_token", "").orEmpty()
    }


    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                showToast("카메라 접근 권한이 필요합니다.")
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        } else {
            showToast("카메라를 실행할 수 없습니다.")
        }
    }


    private fun openSystemPhotoPicker() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            Intent(MediaStore.ACTION_PICK_IMAGES).apply {

            }
        } else {

            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"

            }
        }
        startActivityForResult(intent, PICK_MEDIA_REQUEST_CODE)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openCamera()
                } else {
                    showToast("카메라 권한이 필요합니다.")
                }
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            CAMERA_REQUEST_CODE -> {

                val photo: Bitmap? = data.extras?.get("data") as? Bitmap
                if (photo != null) {
                    imageView.setImageBitmap(photo)
                    uploadImage(photo)
                } else {
                    showToast("사진을 가져올 수 없습니다.")
                }
            }

            PICK_MEDIA_REQUEST_CODE -> {

                val singleUri: Uri? = data.data
                val clip = data.clipData

                if (clip != null && clip.itemCount > 0) {

                    val uri = clip.getItemAt(0).uri
                    handlePickedImageUri(uri, data.flags)
                } else if (singleUri != null) {
                    handlePickedImageUri(singleUri, data.flags)
                } else {
                    showToast("이미지를 선택할 수 없습니다.")
                }
            }
        }
    }

    private fun handlePickedImageUri(uri: Uri, flags: Int) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
            } catch (_: SecurityException) {

            }
        }


        imageView.setImageURI(uri)


        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            uploadImage(bitmap)
        } else {
            showToast("이미지를 불러올 수 없습니다.")
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream.use { stream ->
                if (stream != null) BitmapFactory.decodeStream(stream) else null
            }
        } catch (e: Exception) {
            Log.d("AiPredictActivity", "uriToBitmap 오류: ${e.message}")
            null
        }
    }


    private fun uploadImage(bitmap: Bitmap) {
        val file = convertBitmapToFile("image.jpg", bitmap)

        file?.let {
            val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", it.name, requestFile)

            val apiService = (application as MyApplication).getApiService()

            apiService.predictImage("Bearer $jwtToken", body).enqueue(object : Callback<PredictionResult> {
                override fun onResponse(
                    call: Call<PredictionResult>,
                    response: Response<PredictionResult>
                ) {
                    if (response.isSuccessful) {
                        val result = response.body()
                        if (result != null) {
                            Log.d("AiPredictActivity", "서버 응답 성공: $result")

                            val displayText = "Predicted Class: ${result.predictedLabel}\nDescription: ${result.description}"
                            resultTextView.text = displayText

                            val videoView: WebView = findViewById(R.id.videoWebView)
                            val videoUrl = "<iframe width=\"100%\" height=\"315\" src=\"${result.videoUrl}\" frameborder=\"0\" allowfullscreen></iframe>"
                            videoView.settings.javaScriptEnabled = true
                            videoView.loadData(videoUrl, "text/html", "utf-8")
                        } else {
                            Log.d("AiPredictActivity", "서버 응답이 null입니다.")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.d("AiPredictActivity", "응답 에러: ${response.code()}, $errorBody")
                    }
                }

                override fun onFailure(call: Call<PredictionResult>, t: Throwable) {
                    Log.d("AiPredictActivity", "서버 연결 실패: ${t.message}")
                }
            })
        } ?: run {
            Log.d("AiPredictActivity", "이미지 파일 변환 실패")
        }
    }

    private fun convertBitmapToFile(filename: String, bitmap: Bitmap): File? {
        return try {
            val file = File(applicationContext.cacheDir, filename)
            file.createNewFile()

            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            Log.d("AiPredictActivity", "이미지 파일 변환 성공: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("AiPredictActivity", "이미지 파일 변환 중 오류 발생: ${e.message}")
            null
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
