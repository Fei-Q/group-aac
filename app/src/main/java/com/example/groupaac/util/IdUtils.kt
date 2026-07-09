package com.example.groupaac.util

import java.util.UUID

object IdUtils {
    fun newId(): String = UUID.randomUUID().toString()
}
