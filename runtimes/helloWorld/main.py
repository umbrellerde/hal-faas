import json
import os

import sys
import time
import socket


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, flush=True, **kwargs)


if __name__ == '__main__':
    accelerator = sys.argv[1]
    amount = sys.argv[2]
    try:
        while True:
            params = input()
            # eprint("Got Input: ")
            # eprint(params)
            request = json.loads(params)
            start = time.time()
            if 'sleep' in request['params']:
                time.sleep(request['params']['sleep'])
            else:
                time.sleep(0.05)
            end = time.time()
            result = {
                'request': request,
                'accelerator': accelerator,
                # 'amount': int(amount),
                'pid': str(os.getppid()),
                'result_type': 'value',  # 'reference' or 'value'
                'result': [100],
                'metadata': {
                    'start': start * 1000,
                    'end': end * 1000,
                    'inference_ms': (end - start) * 1000,
                    'hostname': socket.gethostname()
                }
            }
            print(json.dumps(result), flush=True)
    except:
        exit(1)
