package org.burgas.encryption

object RegexType {

    val email = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    val phone = Regex("^(\\+7|7|8)?[0-9]{10}")
}