package com.image.word.converter.convert.docx.ui.navigation

object Routes {
    const val Splash = "splash"
    const val Walkthrough = "walkthrough"
    const val Main = "main?autoSub={autoSub}"
    const val UrlImport = "url_import"
    const val CapturePreview = "capture_preview"
    const val EditPreview = "edit_preview"
    const val Processing = "processing"
    const val Result = "result"
    const val WordPreview = "word_preview/{id}"
    const val Subscription = "subscription"
    const val Adjust = "adjust/{index}"
    const val Filter = "filter/{index}"
    const val Highlighter = "highlighter/{index}"
    const val Signature = "signature/{index}"
    const val TextOverlay = "text_overlay/{index}"

    fun main(autoSub: Boolean = false): String = "main?autoSub=$autoSub"
    fun wordPreview(id: String): String = "word_preview/$id"
    fun adjust(index: Int): String = "adjust/$index"
    fun filter(index: Int): String = "filter/$index"
    fun highlighter(index: Int): String = "highlighter/$index"
    fun signature(index: Int): String = "signature/$index"
    fun textOverlay(index: Int): String = "text_overlay/$index"
}
