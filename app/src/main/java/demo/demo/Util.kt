package demo.demo

import android.os.Environment
import java.io.File
import java.nio.ByteBuffer

fun createNewImageFile(): File {
  val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
      .toString()
  val mkDir = File("$root/Camera2Demo")
  if (!mkDir.exists()) {
    mkDir.mkdirs()
  }
  val imageName = "Image-" + System.currentTimeMillis() + ".jpg"
  return File(mkDir, imageName)
}

fun ByteBuffer.getByteArrayFromBuffer(): ByteArray {
  val bytes = ByteArray(remaining())
  get(bytes)
  return bytes
}