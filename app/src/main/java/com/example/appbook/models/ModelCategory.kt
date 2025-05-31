package com.example.appbook.models

class ModelCategory {

    // variables, must match as in firebase
    var id: String = ""
    var category: String = ""
    var timestamp: Long = 0
    var uid: String = ""

    //empty constructor, required by firebase
    constructor()
    //parameterized constructor
    constructor(category: String, id: String, timestamp: Long, uid: String) {
        this.category = category
        this.id = id
        this.timestamp = timestamp
        this.uid = uid
    }

}