#!/usr/bin/env bash
# could also just run python directly.
# export PIPENV_IGNORE_VIRTUALENVS="1"
docker run -i --rm --device-cgroup-rule='c 189:* rmw' -v /dev/bus/usb:/dev/bus/usb -v /:/code/host onnx-stick python main.py "$@"