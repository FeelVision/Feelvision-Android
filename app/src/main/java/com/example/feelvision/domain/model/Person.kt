package com.feelvision.domain.model

data class Person(
    val id         : Long        = System.currentTimeMillis(),
    val name       : String      = "",
    val relation   : String      = "",
    val notes      : String      = "",
    val photoCount : Int         = 0,
    val embedding  : List<Float> = emptyList(),
    val createdAt  : Long        = System.currentTimeMillis(),
    val updatedAt  : Long        = System.currentTimeMillis()
)