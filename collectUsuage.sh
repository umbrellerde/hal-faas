#!/usr/bin/env bash
while true; do
    res=$(nvidia-smi --query-gpu=gpu_bus_id,utilization.gpu,utilization.memory --format=csv,noheader,nounits)
    time=$(date +%s%N | cut -b1-13)
    echo "$time,$res" >> gpuUsuage.csv
    sleep 1s
done