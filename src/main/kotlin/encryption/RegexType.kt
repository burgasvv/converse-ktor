package org.burgas.encryption

object RegexType {

    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    val phoneRegex = Regex("^(\\+\\d{1,3}[- ]?)?\\d{10}")
}