# Terraform Usuage

0. Cd into this folder in a terminal
1. Run `terraform init` once to initialize terraform
2. Change the file `vars.tf`: `ml_benchmark_location` should be the string pointing to the ml_benchmark git repo
3. Change the `locals` block in the `main.tf` file: The public and private key must point to your default public and private ssh key.
4. Run `terraform apply` to start the VMs. Respond with `yes` if prompted. This will take 5-10mins.
5. terraform will output the string to connect to the SUT. Please remember to run `source activate aws_neuron_pytorch_p36` after login
6. To shut down everything, run `terraform destroy -auto-approve`