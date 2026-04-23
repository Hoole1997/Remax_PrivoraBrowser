package com.browser.shortvideo.player

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

class IFramePlayerOptions private constructor(private val playerOptions: JSONObject) {

    companion object {
        fun getDefault(context: Context) = Builder(context).controls(0).build()
    }

    override fun toString(): String {
        return playerOptions.toString()
    }

    internal fun getOrigin(): String {
        return playerOptions.getString(Builder.ORIGIN)
    }

    class Builder(context: Context) {
        companion object {
            private const val AUTO_PLAY = "autoplay"
            private const val MUTE = "mute"
            private const val CONTROLS = "controls"
            private const val ENABLE_JS_API = "enablejsapi"
            private const val FS = "fs"
            internal const val ORIGIN = "origin"
            private const val REL = "rel"
            private const val IV_LOAD_POLICY = "iv_load_policy"
            private const val CC_LOAD_POLICY = "cc_load_policy"
            private const val PLAYSINLINE = "playsinline"
            private const val LOOP = "loop"
            private const val PLAYLIST = "playlist"
        }

        private val builderOptions = JSONObject()

        init {
            addInt(AUTO_PLAY, 0)
            addInt(MUTE, 0)
            addInt(CONTROLS, 0)
            addInt(ENABLE_JS_API, 1)
            addInt(FS, 0)
            addString(ORIGIN, "https://${context.packageName}")
            addInt(REL, 0)
            addInt(IV_LOAD_POLICY, 3)
            addInt(CC_LOAD_POLICY, 0)
            addInt(PLAYSINLINE, 1)
        }

        fun build(): IFramePlayerOptions {
            return IFramePlayerOptions(builderOptions)
        }

        fun controls(controls: Int): Builder {
            addInt(CONTROLS, controls)
            return this
        }

        fun autoplay(autoplay: Int): Builder {
            addInt(AUTO_PLAY, autoplay)
            return this
        }

        fun mute(mute: Int): Builder {
            addInt(MUTE, mute)
            return this
        }

        fun rel(rel: Int): Builder {
            addInt(REL, rel)
            return this
        }

        fun ivLoadPolicy(ivLoadPolicy: Int): Builder {
            addInt(IV_LOAD_POLICY, ivLoadPolicy)
            return this
        }

        fun ccLoadPolicy(ccLoadPolicy: Int): Builder {
            addInt(CC_LOAD_POLICY, ccLoadPolicy)
            return this
        }

        fun origin(origin: String): Builder {
            addString(ORIGIN, origin)
            return this
        }

        fun fullscreen(fs: Int): Builder {
            addInt(FS, fs)
            return this
        }

        /**
         * 设置循环播放
         * @param loop 1=循环播放, 0=不循环
         */
        fun loop(loop: Int): Builder {
            addInt(LOOP, loop)
            return this
        }

        /**
         * 设置播放列表（循环播放单个视频时需要设置为视频ID）
         * @param playlist 视频ID或播放列表
         */
        fun playlist(playlist: String): Builder {
            addString(PLAYLIST, playlist)
            return this
        }

        private fun addString(key: String, value: String) {
            try {
                builderOptions.put(key, value)
            } catch (e: JSONException) {
                throw RuntimeException("Illegal JSON value $key: $value")
            }
        }

        private fun addInt(key: String, value: Int) {
            try {
                builderOptions.put(key, value)
            } catch (e: JSONException) {
                throw RuntimeException("Illegal JSON value $key: $value")
            }
        }
    }
}
