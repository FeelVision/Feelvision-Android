package com.feelvision.domain.model

data class Person(
    val id: Long = 0,
    val name: String,
    val relation: String,
    val photoCount: Int = 0
)