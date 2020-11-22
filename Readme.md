# HAl-FaaS

To test whether nuclio can use GPUs:

1. Start nuclio locally: `make nuclio`
2. Deploy the execute_command function: `cd nuclio_functions; make execute_command`
3. Go to http://localhost:8070/projects, navigate to the execute_command function and test it with `nvidia-smi` as body parameter.
It should return information about available GPUs.
