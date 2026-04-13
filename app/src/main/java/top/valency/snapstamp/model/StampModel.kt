package top.valency.snapstamp.model

import java.io.File

data class StampModel(
    val fileName: String, 
    val file: File, 
    val date: String, 
    val info: String, 
    val location: String, 
    val remark: String
)