package eu.kanade.tachiyomi.extension.en.prochan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.*
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class Prochan : ParsedHttpSource() {

    override val name = "Prochan"

    override val baseUrl = "https://prochan.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

    // --- Manga List ---

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-list div.item")
            .map {
                SManga.create().apply {
                    val element = it.select("a.title").first() ?: it.select("a").first()
                    title = element?.text() ?: "Unknown"
                    url = element?.attr("href") ?: ""
                    thumbnail_url = it.select("img").attr("abs:src")
                }
            }
        val hasNextPage = document.select("ul.pagination li.next").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // --- Manga Details ---

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.manga-title").text()
            author = document.select("span.author").text()
            description = document.select("div.description").text()
            thumbnail_url = document.select("div.cover img").attr("abs:src")
            genre = document.select("div.genres a").joinToString { it.text() }
            status = parseStatus(document.select("div.status").text())
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // --- Chapters ---

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapter-list li a")
            .map {
                SChapter.create().apply {
                    name = it.text()
                    url = it.attr("href")
                    date_upload = 0L // يمكن تحسينه لاحقاً إذا وجد تاريخ
                }
            }.reversed()
    }

    // --- Pages ---

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-break img").mapIndexed { index, element ->
            Page(index, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("img").attr("abs:src")

    // --- Latest Updates ---

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // --- Search ---

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?query=$query&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)
}
