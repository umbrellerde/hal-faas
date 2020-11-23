.PHONY: nuclio cleanup mqtt

nuclio:
	docker run --gpus all -p 8070:8070 -v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp --rm --name nuclio-dashboard quay.io/nuclio/dashboard:latest-amd64

cleanup:
	@-docker stop nuclio-dashboard
	@-docker stop nuclio-local-storage-reader
	@-docker rm nuclio-local-storage-reader
	@-docker volume rm nuclio-local-storage

mqtt:
	@- docker network create --subnet=172.20.0.0/16 faasnet
	@docker run -it --rm --name mosquitto --net faasnet --ip 172.20.0.6 -p 1883:1883 -p 9001:9001 eclipse-mosquitto