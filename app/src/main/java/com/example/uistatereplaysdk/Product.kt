package com.example.uistatereplaysdk

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val emoji: String
)

val demoProducts = listOf(
    Product("p1", "Matcha Latte", 18, "🍵"),
    Product("p2", "Protein Bar", 12, "🍫"),
    Product("p3", "Running Socks", 35, "🧦"),
    Product("p4", "Wireless Earbuds", 199, "🎧"),
    Product("p5", "Yoga Mat", 89, "🧘‍♀️"),
    Product("p6", "Water Bottle", 45, "🚰")
)

fun findProduct(id: String): Product =
    demoProducts.firstOrNull { it.id == id } ?: demoProducts.first()
