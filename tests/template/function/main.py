import time
from test import test


def current_time():
    return int(round(time.time() * 1000))


def handler(context, event):
    t_start = current_time()
    res = test.test(context, event)
    t_end = current_time()
    context.logger.info("Function complete")
    return {
        "start": t_start,
        "end": t_end,
        "duration": t_end - t_start,
        "worker_id": context.worker_id,
        "result": res,
        "body": str(event.body)
    }
