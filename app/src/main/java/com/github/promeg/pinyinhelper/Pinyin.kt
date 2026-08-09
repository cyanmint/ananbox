package com.github.promeg.pinyinhelper

import android.icu.text.Transliterator
import android.os.Build

/**
 * Minimal, API-compatible drop-in replacement for the `com.github.promeg:tinypinyin`
 * artifact used by the vendored `io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy`
 * (see /NOTICE.md). The original artifact is only published on JCenter/JitPack, both
 * of which are unreachable/unbuildable from this sandboxed environment, so this shim
 * reproduces the small subset of the `Pinyin` object's API (`isChinese`, `toPinyin`)
 * that the vendored code calls, using the platform's own ICU Han-Latin transliterator
 * instead of bundling the third-party lexicon.
 */
object Pinyin {
    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { Transliterator.getInstance("Han-Latin; Latin-ASCII") }.getOrNull()
        } else {
            null
        }
    }

    fun isChinese(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }

    fun toPinyin(c: Char): String {
        val result = transliterator?.let { runCatching { it.transliterate(c.toString()) }.getOrNull() }
        return result?.takeIf { it.isNotBlank() } ?: c.toString()
    }
}
