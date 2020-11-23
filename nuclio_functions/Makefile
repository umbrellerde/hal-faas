.PHONY: execute_command get_context post_http get_worker_id

execute_command:
	@docker build -t execute_command:latest execute_command/
	@nuctl deploy --path execute_command/ --platform local

get_context post_http get_worker_id:
	@docker build -t $@:latest $@/
	@nuctl deploy --path $@/ --platform local