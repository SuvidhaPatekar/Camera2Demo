package demo.demo

import android.os.Environment
import android.view.TextureView
import java.io.File
import java.nio.ByteBuffer

fun createNewImageFile(): File {
  val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
      .toString()
  val mkDir = File("$root/DEMO")

  if (!mkDir.exists()) {
    mkDir.mkdirs()
  }

  val imageName = "Image-" + System.currentTimeMillis() + ".jpg"
  return File(mkDir, imageName)
}

fun getByteArrayFromBuffer(byteBuffer: ByteBuffer): ByteArray {
  val bytes = ByteArray(byteBuffer.remaining())
  byteBuffer.get(bytes)
  return bytes
}
