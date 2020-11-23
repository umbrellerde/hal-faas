# HAl-FaaS

To test whether nuclio can use GPUs:

0. Install `nuctl` from https://github.com/nuclio/nuclio/releases
1. Start nuclio locally: `make nuclio`
2. Deploy the execute_command function: `cd nuclio_functions; make execute_command`
3. Go to http://localhost:8070/projects, navigate to the execute_command function and test it with `nvidia-smi` as body parameter.
It should return information about available GPUs.
4. (To test multiple functions at the same time: change the name (l.4) and port (l.22) of the `function.yaml` file and run `make execute_command` again)
