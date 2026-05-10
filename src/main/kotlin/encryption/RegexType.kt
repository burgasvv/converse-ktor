package org.burgas.encryption

object RegexType {

    val email = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    val phone = Regex("^(\\+\\d{1,10}[- ]?)?\\d{10}")
}