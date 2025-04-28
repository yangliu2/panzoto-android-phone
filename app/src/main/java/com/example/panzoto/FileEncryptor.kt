package com.example.panzoto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object FileEncryptor {
    // For now, a fixed 256-bit (32 bytes) AES key
    private val secretKey: SecretKey = SecretKeySpec(
        "0123456789abcdef0123456789abcdef".toByteArray(),
        "AES"
    )

    fun encryptFile(inputFile: File, outputFile: File) {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        FileInputStream(inputFile).use { fis ->
            FileOutputStream(outputFile).use { fos ->
                CipherOutputStream(fos, cipher).use { cos ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }
}
