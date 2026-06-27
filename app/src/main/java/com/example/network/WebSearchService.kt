package com.example.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

data class SearchResult(
    val title: String,
    val snippet: String,
    val link: String
)

object WebSearchService {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Searches DuckDuckGo HTML interface and returns parsed title, description, and link triplets
     */
    suspend fun search(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            
            val request = Request.Builder()
                .url(url)
                // Add common desktop user-agent to avoid DDG robot detection
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            
            if (html.isEmpty()) {
                Log.w("WebSearch", "Empty response from search query")
                return emptyList()
            }

            // Simple robust HTML parsing using regex pattern matches for result items
            // DuckDuckGo lite format:
            // <div class="web-result"> or <div class="results_links_deep">
            // Inside result list:
            // <a class="result__url" href="URL">Title</a>
            // <a class="result__snippet" ...>Snippet</a>
            
            val resultBlockPattern = Pattern.compile("<div class=\"result results_links results_links_deep.*?</div>\\s*</div>", Pattern.DOTALL)
            val matcher = resultBlockPattern.matcher(html)
            
            while (matcher.find()) {
                val block = matcher.group()
                
                // Extract link and title
                // Form: <a class="result__snippet" href="LINK">...</a> or <a class="result__snippet" ...>
                val titlePattern = Pattern.compile("<a class=\"result__snippet\" href=\"(.*?)\">(.*?)</a>", Pattern.DOTALL)
                val titleMatcher = titlePattern.matcher(block)
                
                var link = ""
                var snippet = ""
                var title = ""
                
                // DuckDuckGo href links usually wrap redirect parameters, e.g. /l/?kh=-1&uddg=REAL_URL
                // Alternatively, let's extract standard link elements
                val linkPattern = Pattern.compile("href=\"(.*?uddg=(.*?))\"", Pattern.DOTALL)
                val linkMatcher = linkPattern.matcher(block)
                if (linkMatcher.find()) {
                    val fullUrl = linkMatcher.group(1)
                    val rawUddg = linkMatcher.group(2)
                    link = java.net.URLDecoder.decode(rawUddg, "UTF-8").split("&").firstOrNull() ?: fullUrl
                }
                
                val textPattern = Pattern.compile("<a class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL)
                val textMatcher = textPattern.matcher(block)
                if (textMatcher.find()) {
                    snippet = cleanHtmlTags(textMatcher.group(1))
                }
                
                // Get general link text as Title
                val headerPattern = Pattern.compile("<a class=\"result__url\"[^>]*>(.*?)</a>", Pattern.DOTALL)
                val headerMatcher = headerPattern.matcher(block)
                if (headerMatcher.find()) {
                    title = cleanHtmlTags(headerMatcher.group(1))
                } else {
                    title = "Web Result: " + (link.substringAfter("://").substringBefore("/").takeIf { it.isNotEmpty() } ?: "Index")
                }
                
                if (link.isNotEmpty() && snippet.isNotEmpty()) {
                    results.add(SearchResult(
                        title = if (title.isEmpty()) "Search Snippet" else title,
                        snippet = snippet,
                        link = link
                    ))
                }
                
                if (results.size >= 5) break // Limit to top 5 results
            }
        } catch (e: Exception) {
            Log.e("WebSearch", "Error performing duckduckgo web search", e)
        }
        return results
    }

    /**
     * Scrapes the raw text content of any website and cleans HTML tags returning pure text paragraphs
     */
    suspend fun scrapeWebsite(url: String): String {
        return try {
            val endpoint = if (url.startsWith("http")) url else "https://$url"
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isEmpty()) return ""

            // Extract body content of document
            val bodyPattern = Pattern.compile("<body[^>]*>(.*?)</body>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = bodyPattern.matcher(html)
            val bodyHtml = if (matcher.find()) matcher.group(1) else html

            // Clean unwanted CSS, style and script blocks from the text
            val scriptStylePattern = Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            var cleanText = scriptStylePattern.matcher(bodyHtml).replaceAll("")

            // Convert common formatting text tags to clean spacing
            cleanText = cleanText.replace("</p>", "\n\n")
            cleanText = cleanText.replace("<br\\s*/?>".toRegex(), "\n")
            cleanText = cleanHtmlTags(cleanText)

            // Normalize white spacing
            val lines = cleanText.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("/*") && !it.startsWith("var ") && it.length > 5 }
            
            lines.joinToString("\n").take(8000) // Truncate content to keep it standard
        } catch (e: Exception) {
            Log.e("WebSearch", "Error scraping web page: $url", e)
            "Error loading webpage content: ${e.localizedMessage}"
        }
    }

    private fun cleanHtmlTags(html: String): String {
        val tagPattern = Pattern.compile("<[^>]*>")
        var text = tagPattern.matcher(html).replaceAll("")
        // Decode common HTML entities
        text = text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&middot;", "·")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&ldquo;", "\"")
            .replace("&rdquo;", "\"")
            .replace("\\s+".toRegex(), " ")
        return text.trim()
    }
}
