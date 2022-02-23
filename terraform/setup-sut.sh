#!/bin/bash
sudo apt-get -qq update
# sudo apt-get -qq install openjdk-11-jre
source activate aws_neuron_pytorch_p36
pip install -q --user pipenv ray ray[tune] tensorboardx
export PATH=$PATH:/home/ubuntu/.local/bin
cd ml_benchmark && pip install -e .
pip --version
pipenv --version
# java -version