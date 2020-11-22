.PHONY: nuclio cleanup

nuclio:
	docker run --gpus all -p 8070:8070 -v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp --rm --name nuclio-dashboard quay.io/nuclio/dashboard:latest-amd64

cleanup:
	@-docker stop nuclio-dashboard
	@-docker stop nuclio-local-storage-reader
	@-docker rm nuclio-local-storage-reader
	@-docker volume rm nuclio-local-storage