.PHONY: deploy mqtt

deploy:
	@docker build -t nuclio-function-test:latest function/
	@nuctl deploy --path function/ --platform local