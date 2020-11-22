import json


def handler(context, event):
    return {
        "context": dir(context),
        "contex.worker_id": context.worker_id,
        "context.trigger_name": context.trigger_name,
        "event": dir(event)
    }
