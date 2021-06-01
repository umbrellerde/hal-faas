# hal-faas
Masters Thesis

## Setting Up
- Create a Minio that the worker nodes have access to
  - It needs a bucket `test` with the files `tinyyolov2-7.onnx` and `input_0_tiny.pb`
  - It also needs a bucket `runtimes` with `onnx.zip`
- Start Bedrock and make sure that all worker nodes and the benchmark client can access it
  - Use the command `rm bedrock.db && touch bedrock.db && ./bedrock`, that usually works. Restart bedrock if a test fails etc to delete the database
- Make sure that all worker nodes can reach the benchmark client HTTP Server that collects the responses
---
- Start the node managers `java -jar noma-1.0-SNAPSHOT-jar-with-dependencies.jar`. Options:
```
    --bedrockHost, -bedrockHost [localhost] { String }
    --bedrockPort, -bedrockPort [8888] { Int }
    --s3AccessKey, -s3access [minio-admin] { String }
    --s3SecretKey, -s3secret [minio-admin] { String }
    --s3Endpoint, -s3host [http://localhost:9000] { String }
```
- Start the Benchmarking Client `java -jar benchmark-client-1.0-SNAPSHOT-jar-with-dependencies.jar`. Options:
```
    --callbackBaseUrl, -callbackBase [localhost:3358] -> Address/Port that will be prepended to the callback url, without a / { String }
    --p0trps, -p0trps [6] { Int }
    --p2trps, -p2trps [20] { Int }
    --p0duration, -p0dur [30000] -> in milliseconds { Int }
    --p1duration, -p1dur [90000] -> in milliseconds { Int }
    --p2duration, -p2dur [30000] -> in milliseconds { Int }
    --runName, -name [fulltest] { String }
```
