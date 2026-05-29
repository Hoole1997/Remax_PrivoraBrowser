package io.docview.push.controller

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.SPUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.docview.push.BuildConfig
import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.corekit.core.ext.DataStoreStringDelegate
import net.corekit.core.report.ReportDataManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Token 上报控制器
 * 负责 FCM Token 的上传和状态管理
 */
object TokenUploadCtrl {

    private const val TAG = "TokenUploadCtrl"
    private const val PREFS_NAME = "token_upload_status"
    private const val KEY_UPLOAD_STATUS_MAP = "upload_status_map"
    private const val SECRET_KEY = "Sn7GxaHco0JGkPMJjZkuzM8iSbt27olK"
    
    // 请求参数名
    private const val PARAM_TOKEN = "tkn"      // token 参数
    private const val PARAM_UID = "sug"      // userid 参数
    private const val PARAM_PACK = "ack"    // package 参数
    private const val HEADER_SIG = "sig"     // 签名 header

    // UUID 持久化存储
    private var uuid by DataStoreStringDelegate("uuuuuuuuii1212ld", "")

    // 全局参数
    private val baseUrl: String = BuildConfig.FCM_URL + "/trav/sum"
    private val packageName: String = BuildConfig.FCM_PKG
    private val gson = Gson()
    
    init {
        // 确保 UUID 已生成
        if (uuid.isNullOrEmpty()) {
            val javaUuid = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            uuid = "${javaUuid}-${timestamp}"
            Logger.d("$TAG 生成新的 UUID: $uuid")
        }
    }

    /**
     * 上传 Token 到服务器
     * @param token FCM Token
     */
    fun uploadToken(token: String) {
        // 检查是否已经上传成功
        if (isTokenUploaded(token)) {
            Logger.d("$TAG Token 已成功上传，跳过重复上传")
            return
        }

        val userid = uuid ?: ""
        val pkg = packageName

        Logger.d("$TAG 开始上传 Token")
        Logger.d("$TAG baseUrl: $baseUrl")
        Logger.d("$TAG token: $token")
        Logger.d("$TAG userid: $userid")
        Logger.d("$TAG pkg: $pkg")

        // 使用协程在后台线程执行网络请求
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 生成签名
                val rsig = generateSignature(token, userid, pkg)

                // 构建完整的请求URL
                val requestUrl = "$baseUrl?$PARAM_TOKEN=$token&$PARAM_UID=$userid&$PARAM_PACK=$pkg"

                Logger.d("$TAG 请求URL: $requestUrl")
                Logger.d("$TAG 签名: $rsig")

                // 创建 OkHttpClient
                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)

                // 如果日志开启，添加日志拦截器
                if (Logger.isLogEnabled()) {
                    val loggingInterceptor = HttpLoggingInterceptor { message ->
                        Logger.d("$TAG OkHttp: $message")
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    clientBuilder.addInterceptor(loggingInterceptor)
                }

                val client = clientBuilder.build()

                // 构建请求
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .addHeader(HEADER_SIG, rsig)
                    .build()

                // 执行请求
                val response = client.newCall(request).execute()

                response.use {
                    val responseCode = it.code
                    val responseBody = it.body?.string() ?: ""

                    Logger.d("$TAG 响应码: $responseCode")
                    Logger.d("$TAG 响应内容: $responseBody")

                    val isSuccess = if (responseBody.isNotEmpty()) {
                        try {
                            // 尝试解析 JSON 响应
                            val jsonObject = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
                            
                            if (jsonObject.has("code")) {
                                // 存在 code 字段，需要同时满足 HTTP 200 和 code 0
                                val codeValue = jsonObject.get("code").asInt
                                val httpOk = it.isSuccessful
                                val codeOk = codeValue == 0
                                
                                Logger.d("$TAG HTTP状态: $httpOk, Code字段: $codeValue, Code状态: $codeOk")
                                httpOk && codeOk
                            } else {
                                // 不存在 code 字段，只判断 HTTP 响应码
                                Logger.d("$TAG 响应中无code字段，仅判断HTTP状态: ${it.isSuccessful}")
                                it.isSuccessful
                            }
                        } catch (e: Exception) {
                            // JSON 解析失败，回退到只判断 HTTP 状态码
                            Logger.d("$TAG JSON解析失败，回退到HTTP状态判断: ${it.isSuccessful}")
                            it.isSuccessful
                        }
                    } else {
                        // 响应体为空，只判断 HTTP 状态码
                        Logger.d("$TAG 响应体为空，仅判断HTTP状态: ${it.isSuccessful}")
                        it.isSuccessful
                    }

                    if (isSuccess) {
                        Logger.d("$TAG Token 上传成功")
                        // 标记为上传成功
                        saveUploadStatus(userid, token, true)
                        ReportDataManager.reportData("fcm_token_report_suc", mapOf("userid" to userid,"token" to token,"pkg" to pkg,"sig" to rsig))
                    } else {
                        Logger.d("$TAG Token 上传失败")
                        // 标记为上传失败
                        saveUploadStatus(userid, token, false)
                        ReportDataManager.reportData("fcm_token_report_fail", mapOf("userid" to userid,"token" to token,"pkg" to pkg,"sig" to rsig))
                    }
                }

            } catch (e: Exception) {
                Logger.d("$TAG Token 上传异常: ${e.message}")
                e.printStackTrace()
                // 标记为上传失败
                saveUploadStatus(uuid ?: "", token, false)
            }
        }
    }

    /**
     * 检查 Token 是否已成功上传
     * @param token FCM Token
     * @return true 表示已成功上传，false 表示未上传或上传失败
     */
    fun isTokenUploaded(token: String): Boolean {
        val key = "${uuid ?: ""}_$token"
        val statusMap = getUploadStatusMap()
        return statusMap[key] == true
    }

    /**
     * 保存 Token 上传状态
     * @param userid 用户ID
     * @param token FCM Token
     * @param success 是否上传成功
     */
    private fun saveUploadStatus(userid: String, token: String, success: Boolean) {
        val key = "${userid}_$token"
        val statusMap = getUploadStatusMap().toMutableMap()
        statusMap[key] = success

        // 保存到 SharedPreferences
        val json = gson.toJson(statusMap)
        SPUtils.getInstance(PREFS_NAME).put(KEY_UPLOAD_STATUS_MAP, json)

        Logger.d("$TAG 保存上传状态: key=$key, success=$success")
    }

    /**
     * 获取 Token 上传状态映射表
     * @return Map<String, Boolean> key为 "uuid_token"，value为上传状态
     */
    private fun getUploadStatusMap(): Map<String, Boolean> {
        val json = SPUtils.getInstance(PREFS_NAME).getString(KEY_UPLOAD_STATUS_MAP, null) ?: return emptyMap()

        return try {
            val type = object : TypeToken<Map<String, Boolean>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Logger.d("$TAG 解析上传状态失败: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 清除所有上传状态记录
     */
    fun clearUploadStatus() {
        SPUtils.getInstance(PREFS_NAME).remove(KEY_UPLOAD_STATUS_MAP)
        Logger.d("$TAG 清除所有上传状态")
    }

    /**
     * 生成请求签名
     * 规则：固定密钥 + 参数按字母升序排序拼接 + MD5
     */
    private fun generateSignature(token: String, userid: String, pkg: String): String {
        // 创建参数Map并按字母升序排序
        val params = mapOf(
            PARAM_PACK to pkg,
            PARAM_TOKEN to token,
            PARAM_UID to userid
        )

        // 按key的字母顺序排序并拼接参数
        val sortedParams = params.toSortedMap()
        val paramString = sortedParams.map { "${it.key}=${it.value}" }.joinToString("&")

        // 拼接固定密钥和参数
        val signString = SECRET_KEY + paramString

        Logger.d("$TAG 签名原始字符串: $signString")

        // 计算MD5
        return md5(signString)
    }

    /**
     * 计算字符串的MD5值
     */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 获取所有上传状态（用于调试）
     * @return Map<String, Boolean>
     */
    fun getAllUploadStatus(): Map<String, Boolean> {
        return getUploadStatusMap()
    }
}

