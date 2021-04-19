#!/usr/bin/env bash
# could also just run python directly.
# export PIPENV_IGNORE_VIRTUALENVS="1"
PIPENV_IGNORE_VIRTUALENVS=1 pipenv run python main.py "$@"