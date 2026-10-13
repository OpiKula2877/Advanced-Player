package com.advancedplayer.ui

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val LIST = "list/{kind}/{key}"
    const val NOW = "now"
    const val QUEUE = "queue"
    const val EQ = "eq"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{id}"
    const val STATS = "stats"
    const val WRAPPED = "wrapped"
    const val CAR = "car"
    const val VIDEO = "video"
    const val VISUALIZER = "visualizer"

    const val KIND_ALBUM = "album"
    const val KIND_ARTIST = "artist"
    const val KIND_FOLDER = "folder"
    const val KIND_PLAYLIST = "playlist"
    const val KIND_SMART = "smart"

    // Empty keys (unknown album/artist) must still form a valid path segment.
    fun list(kind: String, key: String) = "list/$kind/${Uri.encode("k$key")}"
    fun decodeKey(raw: String?) = raw?.removePrefix("k").orEmpty()
    fun edit(id: Long) = "edit/$id"

    val topLevel = setOf(HOME, SEARCH, LIBRARY)
    val fullScreen = setOf(NOW, CAR, VIDEO, VISUALIZER, WRAPPED)
}
