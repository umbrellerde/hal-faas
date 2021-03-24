### Mama

- Put next invocation into one class for more relevant invocations
- (CreateWorkload: put Stuff into minio)

### Noma

- (Get Workloads from Minio)

### Workloads

- On Kotlin Side:
    - Creation of Workloads currently: Noma expects folder with the workloadName, Mama expects CreateWorkload with name
      of this folder/other config.
    - So in final version: Client uploads files to S3 and then calls Mama with config.
    - How to set up the right accelerator? Pass in Invoke!!
- Python Side:
    - Create Example Workload that gets images from a minio container
    - Traffic set hash from prev. project??