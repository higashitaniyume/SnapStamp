package top.valency.snapstamp.data.repository

import android.content.Context
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import top.valency.snapstamp.model.StampModel
import top.valency.snapstamp.utils.decodeRemark
import top.valency.snapstamp.utils.encodeRemark
import java.io.File
import java.util.Locale

class StampRepository(private val context: Context) {

    suspend fun getStamps(): List<StampModel> = withContext(Dispatchers.IO) {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val baseDir = File(externalDir, "SnapStamp/Raw")

        if (!baseDir.exists()) return@withContext emptyList()

        val files = baseDir.walk()
            .filter { f ->
                f.isFile && f.name.startsWith("STAMP_") && f.name.endsWith(".jpg") && !f.name.contains("_OIL")
            }
            .toList()
            .sortedByDescending { it.lastModified() }

        val mapped = mutableListOf<StampModel>()
        for (f in files) {
            yield()
            val exif = ExifInterface(f.absolutePath)
            mapped.add(
                StampModel(
                    fileName = f.name,
                    file = f,
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "Unknown Date",
                    info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Unknown Device",
                    location = exif.latLong?.let { String.format(Locale.US, "%.2f, %.2f", it[0], it[1]) } ?: "Unknown Location",
                    remark = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeRemark(it) } ?: ""
                )
            )
        }
        mapped
    }

    suspend fun deleteStamps(stamps: List<StampModel>) = withContext(Dispatchers.IO) {
        stamps.forEach { stamp ->
            if (stamp.file.exists()) stamp.file.delete()
            val oilFile = File(stamp.file.absolutePath.replace(".jpg", "_OIL.jpg"))
            if (oilFile.exists()) oilFile.delete()
        }
    }

    suspend fun updateRemark(stamp: StampModel, remark: String) = withContext(Dispatchers.IO) {
        val exif = ExifInterface(stamp.file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, encodeRemark(remark))
        exif.saveAttributes()
    }
}
