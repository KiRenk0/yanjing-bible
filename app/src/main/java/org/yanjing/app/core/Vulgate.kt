package org.yanjing.app.core

internal data class LatinBook(val book: Int,val title: String,val english: String,val canonicalBook: Int?,val counts: List<Int>)

object Vulgate {
    const val verses=35809
    val books: List<Int> get() = vulgateBooks.map { it.book }
    val verseCounts: Map<Int,List<Int>> by lazy { vulgateBooks.associate { it.book to it.counts } }
    fun bookName(book: Int) = vulgateBooks.firstOrNull { it.book==book }?.title.orEmpty()
    fun chapterCount(book: Int) = verseCounts[book]?.size ?: 0
    fun parse(raw: String,normalizer: TextNormalizer): ReferenceQuery? {
        val text=normalizer.script(raw).replace(Regex("〔拉丁文〕|\\[拉丁文\\]"),"").trim()
        val match=Regex("^(.+?)\\s*(\\d+)\\s*(?:(?::|章)\\s*(\\d+)\\s*(?:[-–—]\\s*(\\d+))?\\s*节?)?\\s*章?$").matchEntire(text) ?: return null
        val name=match.groupValues[1].filterNot(Char::isWhitespace)
        val book=vulgateBooks.firstOrNull { b ->
            (listOf(b.title,b.english)+Books.aliases().filterValues { it==b.canonicalBook }.keys).any { normalizer.script(it).filterNot(Char::isWhitespace)==name }
        }?.book ?: return null
        val chapter=match.groupValues[2].toIntOrNull() ?: return null
        val start=match.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
        val end=match.groupValues[4].takeIf(String::isNotEmpty)?.toIntOrNull() ?: start
        return ReferenceQuery(book,chapter,start,end)
    }
}
