import time


def test(context, event):
    context.logger.info("Starting function")
    time.sleep(2)
    context.logger.info("Stopping function")
    return 2
