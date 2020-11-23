import subprocess
import time


def handler(context, event):
    return subprocess.check_output(event.body)
