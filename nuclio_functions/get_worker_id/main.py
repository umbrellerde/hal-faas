import time


def handler(context, event):
    time.sleep(5)
    return context.worker_id
