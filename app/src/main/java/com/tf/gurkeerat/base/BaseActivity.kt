package com.tf.gurkeerat.base

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

abstract class BaseActivity : ComponentActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

    }

    fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<Detection>, angle : Float
    ): Bitmap {
        val finalBitmap = bitmap.rotate(angle)
        val outputBitmap = finalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        val textBGPaint = Paint()
        val bounds = Rect()
        pen.textAlign = Paint.Align.LEFT

        for (result in detectionResults) {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 2F
            pen.style = Paint.Style.STROKE
            val boundingBox = result.boundingBox

            val top = boundingBox.top * 1F
            val bottom = boundingBox.bottom * 1F
            val left = boundingBox.left * 1F
            val right = boundingBox.right * 1F

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            val names = result.categories[0].label
            // here i am getting hashcode for the label and converting that hashcode into rgb color format
            val color = hashCodeToColor(names.hashCode())
            pen.color = color
            canvas.drawRect(drawableRect, pen)

            // style for text
            textBGPaint.color = Color.BLACK
            textBGPaint.style = Paint.Style.FILL
            textBGPaint.textSize = 20f
            pen.style = Paint.Style.FILL
            pen.color = Color.WHITE

            pen.textSize = 20F
            val drawableText =
                result.categories[0].label + " " +
                        String.format("%.2f", result.categories[0].score)
            pen.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val fontSize: Float = pen.textSize * drawableRect.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize
            textBGPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + 2,
                top + textHeight + 2,
                textBGPaint
            )
            var margin = (drawableRect.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                drawableText, left,
                top + bounds.height(), pen
            )
        }
        return outputBitmap
    }

    // this method saves the image to gallery
    fun saveMediaToStorage(bitmap: Bitmap) {
        // Generating a file name
        val filename = "${System.currentTimeMillis()}.jpg"

        // Output stream
        var fos: OutputStream? = null

        // For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getting the contentResolver
            this.contentResolver?.also { resolver ->

                // Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    // putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                // Inserting the contentValues to
                // contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                // Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            // These for devices running on android < Q
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            // Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(this, "Screenshot taken successfully!", Toast.LENGTH_SHORT).show()
        }
    }


    fun getConvertedValue(intVal: Int): Float {
        var floatVal = 0.0f
        floatVal = 0.1f * intVal
        return floatVal
    }


    private fun hashCodeToColor(hashCode: Int): Int {
        val r = (hashCode and 0xFF0000 shr 16) and 0xFF
        val g = (hashCode and 0x00FF00 shr 8) and 0xFF
        val b = hashCode and 0x0000FF
        return Color.rgb(r, g, b)
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }



}