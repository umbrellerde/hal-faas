//import io.minio.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile

class S3Helper {
    companion object {

        fun getInputConfiguration(bucket: String, objectName: String): File {
            return File("")
        }

        fun getInputData(s3obj: S3File): File {
            return File("")
        }
        fun getRuntime(runtimeName: String) {

        }

        fun uploadFiles(filepath: List<String>, bucket: S3Bucket): List<String> {
            return emptyList()
        }

//        private val folder = File("s3cache/").apply { mkdirs() }
//        private val configFolder = File(folder, "configs/").apply { mkdirs() }
//        private val dataFolder = File(folder, "data/").apply { mkdirs() }
//        private val runtimeFolder = File("runtimes/").apply { mkdirs() }
//        private val runtimeZipFolder = File("runtimes-zip/").apply { mkdirs() }
//
//        private val s3Client = MinioClient.builder().endpoint(Settings.s3Endpoint).credentials(
//            Settings.s3AccessKey,
//            Settings.s3SecretKey
//        ).build()
//        private val logger = KotlinLogging.logger {}
//        private val lockedFiles = mutableMapOf<String, ReentrantLock>()
//
//
//        fun download(
//            bucket: String,
//            objectName: String,
//            subfolder: File,
//            s3Client: MinioClient = Companion.s3Client
//        ): File {
//            assert(subfolder.isDirectory)
//            val filePath = subfolder.absolutePath + File.separator + objectName
//            val lock = synchronized(lockedFiles) {
//                if (lockedFiles[filePath] == null) {
//                    val lock = ReentrantLock()
//                    lockedFiles[filePath] = lock
//                    lock
//                } else {
//                    lockedFiles[filePath]!!
//                }
//            }
//
//            lock.lock()
//            // If the file does not already exist, download it.
//            if (!File(filePath).exists()) {
//                logger.debug { "Downloading $bucket / $objectName to $subfolder" }
//
//                s3Client.downloadObject(
//                    DownloadObjectArgs.builder().bucket(bucket).`object`(objectName)
//                        .filename(filePath)
//                        .build()
//                )
//                logger.debug { "Created File @ ${File(subfolder, objectName).absolutePath}" }
//            }
//            lock.unlock()
//
//            // If there is no routine waiting for this lock delete it.
//            // This should save some heap memory
//            GlobalScope.launch {
//                synchronized(lockedFiles) {
//                    if (lockedFiles[filePath]!!.holdCount == 0) {
//                        lockedFiles.remove(filePath)
//                    }
//                }
//            }
//
//            return File(subfolder, objectName)
//        }
//
//        fun getInputConfiguration(bucket: String, objectName: String): File {
//            val bucketFolder = File(configFolder, "$bucket/").apply { mkdirs() }
//            val destFile = File(bucketFolder, objectName)
//
//            return if (destFile.exists()) {
//                logger.debug { "Input File $bucket / $objectName already exists" }
//                destFile
//            } else {
//                logger.debug { "getInputConfig: downloading $objectName" }
//                download(bucket, objectName, bucketFolder)
//            }
//        }
//
//        fun getInputData(s3obj: S3File): File {
//            val bucketFolder = File(dataFolder, "${s3obj.bucket.bucketName}/").apply { mkdirs() }
//            val destFile = File(bucketFolder, s3obj.file)
//
//            return if (destFile.exists()) {
//                logger.debug { "Data File $s3obj already exists" }
//                destFile
//            } else {
//                val clientsClient = MinioClient.builder().endpoint(s3obj.bucket.endpoint).credentials(
//                    s3obj.bucket.accessKey,
//                    s3obj.bucket.secretKey
//                ).build()
//                logger.debug { "getInputData: Downloading file $s3obj" }
//                download(s3obj.bucket.bucketName, s3obj.file, bucketFolder, clientsClient)
//            }
//        }
//
//        /**
//         * checks if the runtime is already downloaded and downloads it if necessary
//         */
//        fun getRuntime(runtimeName: String) {
//            val alreadyThere = File(runtimeFolder, "$runtimeName/").exists()
//
//            if (!alreadyThere) {
//                logger.debug { "getRuntime: downloading Runtime $runtimeName" }
//                val zipFile = download("runtimes", "$runtimeName.zip", runtimeZipFolder)
//                val extractedFolder = File(runtimeFolder, "$runtimeName/").apply { mkdirs() }
//                // https://stackoverflow.com/questions/46627357/unzip-a-file-in-kotlin-script-kts
//                // modified to use the right folder and maybe create directories
//                ZipFile(zipFile).use { zip ->
//                    zip.entries().asSequence().forEach { entry ->
//                        zip.getInputStream(entry).use { input ->
//                            File(extractedFolder, entry.name).outputStream().use { output ->
//                                input.copyTo(output)
//                            }
//                        }
//                    }
//                }
//                File(extractedFolder, "startup.sh").apply { setExecutable(true) }
//            } else {
//                logger.debug { "getRuntime: Runtime $runtimeName already downloaded" }
//            }
//        }
//
//        /**
//         * upload the list of files to the bucket
//         */
//        fun uploadFiles(filepath: List<String>, bucket: S3Bucket): List<String> {
//            val clientsClient = MinioClient.builder().endpoint(bucket.endpoint).credentials(
//                bucket.accessKey,
//                bucket.secretKey
//            ).build()
//            if (!clientsClient.bucketExists(BucketExistsArgs.builder().bucket(bucket.bucketName).build())) {
//                clientsClient.makeBucket(MakeBucketArgs.builder().bucket(bucket.bucketName).build())
//            }
//            for (file in filepath) {
//                clientsClient.uploadObject(
//                    UploadObjectArgs.builder()
//                        .bucket(bucket.bucketName)
//                        .`object`(file.split("/").last())
//                        .filename(file)
//                        .build()
//                )
//            }
//            return filepath.map { it.split("/").last() }
//        }
    }
}