package com.example.test.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudinaryUploader {

    private const val CLOUD_NAME    = "degikq6ti"
    private const val UPLOAD_PRESET = "music_app_uploads"
    private const val UPLOAD_URL    = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads the image at [uri] to Cloudinary and returns the secure https:// URL.
     * Throws on failure so callers can catch and show a toast.
     */
    suspend fun upload(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read image from URI")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "upload.jpg",
                bytes.toRequestBody("image/*".toMediaTypeOrNull())
            )
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw RuntimeException("Empty response from Cloudinary")

        if (!response.isSuccessful) {
            throw RuntimeException("Cloudinary upload failed (${response.code}): $body")
        }

        JSONObject(body).getString("secure_url")
    }
}