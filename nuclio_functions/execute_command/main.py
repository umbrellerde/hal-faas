import subprocess


def handler(context, event):
    return subprocess.check_output(event.body)
