package com.goodwy.smsmessenger.helpers

import android.util.LruCache
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.models.NamePhoto

private const val CACHE_SIZE = 512

object MessagingCache {
    val namePhoto = LruCache<String, NamePhoto>(CACHE_SIZE)
    val participantsCache = LruCache<Long, ArrayList<SimpleContact>>(CACHE_SIZE)
}
