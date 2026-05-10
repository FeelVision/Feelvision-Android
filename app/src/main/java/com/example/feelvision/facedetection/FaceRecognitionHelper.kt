package com.feelvision.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import com.feelvision.data.people.PeopleRepository
import com.feelvision.domain.model.Person
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class FaceRecognitionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "FaceRecognitionHelper"
    private var faceDetector: FaceDetector? = null
    private var faceEmbeddingInterpreter: Interpreter? = null

    /**
     * Initializes the MediaPipe Face Detector and TFLite Interpreter for MobileFaceNet
     */
    @Synchronized
    fun lazyInit() {
        if (faceDetector != null && faceEmbeddingInterpreter != null) return

        try {
            if (faceDetector == null) {
                Log.d(TAG, "Initializing MediaPipe Face Detector...")
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("blaze_face_short_range.tflite")
                    .build()

                val options = FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(0.5f)
                    .build()

                faceDetector = FaceDetector.createFromOptions(context, options)
                Log.d(TAG, "MediaPipe Face Detector initialized successfully.")
            }

            if (faceEmbeddingInterpreter == null) {
                Log.d(TAG, "Initializing MobileFaceNet Interpreter...")
                val modelBuffer = loadModelFile(context, "mobilefacenet.tflite")
                faceEmbeddingInterpreter = Interpreter(modelBuffer)
                Log.d(TAG, "MobileFaceNet Interpreter initialized successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face recognition models: ${e.message}", e)
        }
    }

    /**
     * Detects faces in the given bitmap and returns their bounding boxes
     */
    @Synchronized
    fun detectFaces(bitmap: Bitmap): List<RectF> {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return emptyList()
        try {
            lazyInit()
            val detector = faceDetector ?: return emptyList()

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = detector.detect(mpImage)
            Log.e(TAG, result.detections().toString())
            return result.detections().map { it.boundingBox() }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Crops the face from the bitmap using the bounding box, resizes to 112x112,
     * extracts pixels, normalizes, and runs inference to generate the face embedding.
     */
    @Synchronized
    fun extractEmbedding(bitmap: Bitmap, faceBox: RectF): FloatArray? {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return null
        var faceBitmap: Bitmap? = null
        var resizedFace: Bitmap? = null
        try {
            lazyInit()
            val interpreter = faceEmbeddingInterpreter ?: return null

            // 1. Clamp bounding box coordinates to bitmap size
            val left = maxOf(0f, faceBox.left).toInt()
            val top = maxOf(0f, faceBox.top).toInt()
            val right = minOf(bitmap.width.toFloat(), faceBox.right).toInt()
            val bottom = minOf(bitmap.height.toFloat(), faceBox.bottom).toInt()

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Invalid face box width/height: $width x $height")
                return null
            }

            // 2. Crop face from bitmap
            faceBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)

            // 3. Resize to 112x112 (standard input size for MobileFaceNet)
            resizedFace = Bitmap.createScaledBitmap(faceBitmap, 112, 112, true)

            // 4. Preprocess pixels into a Float ByteBuffer [1, 112, 112, 3]
            val inputBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4) // Float is 4 bytes
            inputBuffer.order(ByteOrder.nativeOrder())
            inputBuffer.rewind()

            val intValues = IntArray(112 * 112)
            resizedFace.getPixels(intValues, 0, 112, 0, 0, 112, 112)

            for (pixel in intValues) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Normalization: (value - 127.5) / 128
                inputBuffer.putFloat((r - 127.5f) / 128f)
                inputBuffer.putFloat((g - 127.5f) / 128f)
                inputBuffer.putFloat((b - 127.5f) / 128f)
            }

            // 5. Run MobileFaceNet inference (outputs 192-D vector)
            val outputArray = Array(1) { FloatArray(192) }
            interpreter.run(inputBuffer, outputArray)

            // 6. Return L2 normalized embedding
            return l2Normalize(outputArray[0])
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face embedding: ${e.message}", e)
            return null
        } finally {
            faceBitmap?.let { if (!it.isRecycled && it != bitmap) it.recycle() }
            resizedFace?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private val photoEmbeddingCache = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()

    /**
     * Gets or extracts the embedding for a specific photo file, caching it for subsequent calls.
     */
    fun getOrExtractPhotoEmbedding(photoFile: File): FloatArray? {
        val path = photoFile.absolutePath
        photoEmbeddingCache[path]?.let { return it }

        if (!photoFile.exists()) return null
        try {
            val opt = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(path, opt) ?: return null
            val faces = detectFaces(bitmap)
            if (faces.isNotEmpty()) {
                val largestFace = faces.maxByOrNull { bbox ->
                    (bbox.right - bbox.left) * (bbox.bottom - bbox.top)
                }
                if (largestFace != null) {
                    val embedding = extractEmbedding(bitmap, largestFace)
                    if (embedding != null) {
                        photoEmbeddingCache[path] = embedding
                        bitmap.recycle()
                        return embedding
                    }
                }
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting embedding for photo $path: ${e.message}", e)
        }
        return null
    }

    /**
     * Computes face embeddings for any enrolled individuals who do not have one saved.
     */
    suspend fun ensureAllEmbeddingsComputed(repo: PeopleRepository) = withContext(Dispatchers.IO) {
        try {
            val people = repo.getAllPeople().first()
            for (person in people) {
                if (person.embedding.isEmpty() && person.photoCount > 0) {
                    Log.d(TAG, "Generating face embedding for: ${person.name}")
                    val photos = repo.getPhotos(person.id)
                    if (photos.isEmpty()) continue

                    val embeddingsList = mutableListOf<FloatArray>()
                    for (photoFile in photos) {
                        val embedding = getOrExtractPhotoEmbedding(photoFile)
                        if (embedding != null) {
                            embeddingsList.add(embedding)
                        }
                    }

                    if (embeddingsList.isNotEmpty()) {
                        // Average element-wise and L2-normalize
                        val avgEmbedding = FloatArray(192)
                        for (emb in embeddingsList) {
                            for (i in 0 until 192) {
                                avgEmbedding[i] += emb[i]
                            }
                        }
                        for (i in 0 until 192) {
                            avgEmbedding[i] /= embeddingsList.size
                        }
                        val normalizedAvg = l2Normalize(avgEmbedding)
                        repo.updateEmbedding(person.id, normalizedAvg.toList())
                        Log.d(TAG, "Successfully saved averaged embedding of ${embeddingsList.size} photos for ${person.name}")
                    } else {
                        Log.w(TAG, "Could not detect any faces in the ${photos.size} saved photos of ${person.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in background embedding compilation: ${e.message}", e)
        }
    }

    /**
     * Compares a given face embedding against every other enrolled person and their individual photos,
     * logging the L2 Distance and Cosine Similarity.
     */
    suspend fun logDetailedSimilarityScores(capturedEmbedding: FloatArray, repo: PeopleRepository) = withContext(Dispatchers.IO) {
        try {
            val people = repo.getAllPeople().first()
            Log.d(TAG, "=====================================================================")
            Log.d(TAG, "DETAILED FACE SIMILARITY REPORT")
            Log.d(TAG, "=====================================================================")
            Log.d(TAG, people.size.toString())
            for (person in people) {
                Log.d(TAG, "👤 Person: ${person.name} (${person.relation})")
                if (person.embedding.isNotEmpty()) {
                    val personDist = l2Distance(capturedEmbedding, person.embedding.toFloatArray())
                    val personSim = (1.0f - (personDist * personDist) / 2.0f) * 100f
                    Log.d(TAG, "   └─ Average Profile Embedding Similarity: L2_Dist = ${"%.4f".format(personDist)} | Cos_Sim = ${"%.2f".format(personSim)}%")
                } else {
                    Log.d(TAG, "   └─ Average Profile Embedding Similarity: [No average embedding computed yet]")
                }

                val photos = repo.getPhotos(person.id)
                if (photos.isNotEmpty()) {
                    Log.d(TAG, "   └─ Individual Enrolled Photos (${photos.size}):")
                    for (photoFile in photos) {
                        val photoEmbedding = getOrExtractPhotoEmbedding(photoFile)
                        if (photoEmbedding != null) {
                            val photoDist = l2Distance(capturedEmbedding, photoEmbedding)
                            val photoSim = (1.0f - (photoDist * photoDist) / 2.0f) * 100f
                            Log.d(TAG, "      ├── Photo: ${photoFile.name} | L2_Dist = ${"%.4f".format(photoDist)} | Cos_Sim = ${"%.2f".format(photoSim)}%")
                        } else {
                            Log.d(TAG, "      ├── Photo: ${photoFile.name} | [Failed to extract face embedding]")
                        }
                    }
                } else {
                    Log.d(TAG, "   └─ Individual Enrolled Photos: [No photos enrolled]")
                }
            }
            Log.d(TAG, "=====================================================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating detailed similarity scores: ${e.message}", e)
        }
    }

    /**
     * Normalizes a float vector to have an L2 norm of 1.0
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }
        val norm = sqrt(sum)
        if (norm == 0f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    /**
     * Computes the Euclidean distance between two float vectors.
     * Both vectors should be L2-normalized first.
     */
    fun l2Distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Releases model resources
     */
    @Synchronized
    fun close() {
        try {
            faceDetector?.close()
            faceDetector = null
            faceEmbeddingInterpreter?.close()
            faceEmbeddingInterpreter = null
            Log.d(TAG, "Face recognition models closed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing face recognition models: ${e.message}", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        context.assets.openFd(modelPath).use { fileDescriptor ->
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}
