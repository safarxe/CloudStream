package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack

class StreamRubyExtractor(
) : ExtractorApi() {
    override val name            = "StreamRuby"
    override val mainUrl         = "https://rubyvidhub.com/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extRef = referer ?: ""
        Log.d("Kekik_${this.name}", "url » $url")

        val iSource = app.get(url, referer = extRef)
        val obfuscatedScript = iSource.document.select("script").find { it.data().contains("eval(function(p,a,c,k,e") }?.data()?.trim()
        val rawScript        = getAndUnpack(obfuscatedScript!!)
        val source = rawScript.substringAfter("sources:[").substringBefore("],").addMarks("file")
        Log.d("Kekik_${this.name}", "source » $source")
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val fileSource: FileSource = objectMapper.readValue(source)
        val lastUrl = fileSource.file.toString()
        Log.d("Kekik_${this.name}", "lastUrl » $lastUrl")

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = lastUrl,
                referer = extRef,
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )
    }

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }
}

private data class FileSource(
    @JsonProperty("file") val file: String? = null,
)