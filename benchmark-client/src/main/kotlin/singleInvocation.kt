fun main(args: Array<String>) {
    Settings.set(args)
    val bc = BedrockClient()
    val inv =
        Invocation(
            "onnx", "test|yolov4.onnx", InvocationParams(
                PayloadTypes.REFERENCE, S3File(
                    S3Bucket(bucketName = "test"),
                    "input1.pb"
                ), "", S3Bucket(bucketName = "results"),
                "localhost:9001/" + RandomIDGenerator.next()
            )
        )
    bc.createInvocation(inv)
    while(true) {
        println("Press enter to create a new invocation:")
        when(readLine()) {
            "" -> bc.createInvocation(inv)
            else -> break
        }
    }
}