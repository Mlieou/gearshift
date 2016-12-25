package org.sugr.gearshift.viewmodel.api.transmission

import com.google.gson.annotations.SerializedName
import org.sugr.gearshift.model.Session

data class TransmissionSession(
        @SerializedName("download-dir")
        val downloadDir: String = "",
        @SerializedName("speed-limit-down-enabled")
        val downloadSpeedLimitEnabled: Boolean = false,
        @SerializedName("speed-limit-up-enabled")
        val uploadSpeedLimitEnabled: Boolean = false,
        @SerializedName("alt-speed-enabled")
        val altSpeedLimitEnabled: Boolean = false,
        @SerializedName("alt-speed-down")
        val altDownloadSpeedLimit: Long = -1,
        @SerializedName("alt-speed-up")
        val altUploadSpeedLimit: Long = -1,
        @SerializedName("rpc-version")
        val rpcVersion: Int = 0,
        @SerializedName("speed-limit-down")
        override val downloadSpeedLimit: Long = -1,
        @SerializedName("speed-limit-up")
        override val uploadSpeedLimit: Long = -1,
        @SerializedName("seedRatioLimited")
        override val seedRatioLimitEnabled: Boolean = false,
        @SerializedName("seedRatioLimit")
        override val seedRatioLimit: Float = -1f
) : Session
