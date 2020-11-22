.PHONY: execute_command get_context

execute_command:
	@docker build -t execute_command:latest execute_command/
	@nuctl deploy --path execute_command/ --platform local

get_context:
	@docker build -t $@:latest $@/
	@nuctl deploy --path $@/ --platform local