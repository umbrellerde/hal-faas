import requests
import json


def handler(context, event):
    b = json.loads(event.body)
    context.logger.info(b)
    url = str(b["url"])
    body = str(b["body"])
    res = requests.post(url, body)
    return res.content
