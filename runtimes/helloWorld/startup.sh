#!/usr/bin/env bash
# could also just run python directly.
pipenv install &>/dev/null
pipenv run python main.py "$@"