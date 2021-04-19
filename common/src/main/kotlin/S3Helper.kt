import io.minio.DownloadObjectArgs
import io.minio.MinioClient
import io.minio.UploadObjectArgs
import mu.KotlinLogging
import java.io.File

class S3Helper {
    companion object {

        private val folder = File("s3cache/").apply { mkdirs() }
        private val configFolder = File(folder, "configs/").apply { mkdirs() }
        private val dataFolder = File(folder, "data/").apply { mkdirs() }

        private val s3Client = MinioClient.builder().endpoint(Settings.s3Endpoint).credentials(
            Settings.s3AccessKey,
            Settings.s3SecretKey
        ).build()
        private val logger = KotlinLogging.logger {}

        fun download(
            bucket: String,
            objectName: String,
            subfolder: File,
            s3Client: MinioClient = Companion.s3Client
        ): File {
            assert(subfolder.isDirectory)
            logger.debug { "Downloading $bucket / $objectName to $subfolder" }
            s3Client.downloadObject(
                DownloadObjectArgs.builder().bucket(bucket).`object`(objectName)
                    .filename(subfolder.absolutePath + File.separator + objectName)
                    .build()
            )
            logger.debug { "Created File @ ${File(subfolder, objectName).absolutePath}" }
            return File(subfolder, objectName)
        }

        fun getInputConfiguration(bucket: String, objectName: String): File {
            val bucketFolder = File(configFolder, "$bucket/").apply { mkdirs() }
            val destFile = File(bucketFolder, objectName)

            return if (destFile.exists()) {
                logger.debug { "Input File $bucket / $objectName already exists" }
                destFile
            } else {
                download(bucket, objectName, bucketFolder)
            }
        }

        fun getInputData(s3obj: S3File): File {
            val bucketFolder = File(dataFolder, "${s3obj.bucket.bucketName}/").apply { mkdirs() }
            val destFile = File(bucketFolder, s3obj.file)

            return if (destFile.exists()) {
                logger.debug { "Data File $s3obj already exists" }
                destFile
            } else {
                val clientsClient = MinioClient.builder().endpoint(s3obj.bucket.endpoint).credentials(
                    s3obj.bucket.accessKey,
                    s3obj.bucket.secretKey
                ).build()
                download(s3obj.bucket.bucketName, s3obj.file, bucketFolder, clientsClient)
            }
        }

        /**
         * upload the list of files to the bucket
         */
        fun uploadFiles(filepath: List<String>, bucket: S3Bucket): List<String> {
            val clientsClient = MinioClient.builder().endpoint(bucket.endpoint).credentials(
                bucket.accessKey,
                bucket.secretKey
            ).build()
            for (file in filepath) {
                clientsClient.uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucket.bucketName)
                        .`object`(file.split("/").last())
                        .filename(file)
                        .build()
                )
            }
            return filepath.map { it.split("/").last() }
        }
    }
}