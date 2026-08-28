// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element

class UgurFilm : MainAPI() {
    override var mainUrl             = "https://ugurfilm1.xyz"
    override var name                = "UgurFilm"
    override val hasMainPage         = true
    override var lang                = "tr"
    override val hasQuickSearch      = false
    override val supportedTypes      = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/turkce-altyazili-filmler/page/" to "Türkçe Altyazılı Filmler",
        "${mainUrl}/yerli-filmler/page/"            to "Yerli Filmler",
        "${mainUrl}/en-cok-izlenen-filmler/page/"   to "En Çok İzlenen Filmler",
        "${mainUrl}/category/kisa-film/page/"      to "Kısa Film",
        "${mainUrl}/category/aksiyon/page/"        to "Aksiyon",
        "${mainUrl}/category/bilim-kurgu/page/"    to "Bilim Kurgu",
        "${mainUrl}/category/belgesel/page/"       to "Belgesel",
        "${mainUrl}/category/komedi/page/"         to "Komedi",
        "${mainUrl}/category/kara-film/page/"      to "Kara Film",
        "${mainUrl}/category/erotik/page/"         to "Erotik",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.icerik div.frag-k").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("a.baslik span")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a.resim")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a.resim img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.icerik div.frag-k").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title          = document.selectFirst("div.bilgi h2")?.text()?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val poster         = fixUrlNull(document.selectFirst("div#slayt div.resim img")?.attr("src"))
        val year           = document.selectFirst("div.bilgi a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()
        val description    = document.selectFirst("div.bilgi div.slayt-aciklama")?.text()?.trim()
        val tags           = document.select("div.bilgi p.tur a[href*='/category/']").map { it.text() }
        val rating         = document.selectFirst("div#slayt span.puan")?.text()?.split(":")?.lastOrNull()?.trim()
        val duration       = document.selectFirst("div.bilgi p:contains(Süre:) b")?.text()?.split(" ")?.firstOrNull()?.trim()?.toIntOrNull()
        val actors         = document.select("li.oyuncu-k").mapNotNull {
            val name = it.selectFirst("span.isim")?.text()
            val image = it.selectFirst("img")?.attr("src")
            if (name != null) Actor(name, image) else null
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.score     = Score.from10(rating)
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val mainDocument = app.get(data).document

        mainDocument.select("ul#f_part li.parttab a").forEach {
            val subUrl  = fixUrlNull(it.attr("href")) ?: return@forEach
            val document = if (subUrl == data) mainDocument else app.get(subUrl).document

            val iframe   = fixUrlNull(document.selectFirst("div#vast iframe")?.attr("src")) ?: return@forEach

            if (iframe.contains(mainUrl)) {
                val kaynaklar = app.get(iframe, referer=data).document.select("li.c-dropdown__item").associate { kaynak ->
                    kaynak.attr("data-dropdown-value") to kaynak.attr("data-order-value")
                }

                val vidId    = iframe.substringAfter("/play.php?vid=").trim()
                val yuklenenler = mutableListOf<String>()

                for ((kaynak, order) in kaynaklar) {
                    val playerApi = app.post(
                        "${mainUrl}/player/ajax_sources.php",
                        data = mapOf(
                            "vid"         to vidId,
                            "alternative" to kaynak,
                            "ord"         to order
                        )
                    ).text
                    
                    val playerData = AppUtils.tryParseJson<AjaxSource>(playerApi) ?: continue

                    if (playerData.iframe in yuklenenler) continue

                    yuklenenler.add(playerData.iframe)
                    loadExtractor(playerData.iframe.replace("vidmoly.me", "vidmoly.net"), "${mainUrl}/", subtitleCallback, callback)
                }
            } else {
                loadExtractor(iframe.replace("vidmoly.me", "vidmoly.net"), "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }
}