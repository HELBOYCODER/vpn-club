package dev.valid7996.narcicgeteway.platform

internal expect fun secureRandomToken(length: Int): String

internal const val TOKEN_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
