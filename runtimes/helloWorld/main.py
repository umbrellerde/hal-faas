import json
import os
import sys
import time


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
            result = {
                'request': request,
                'accelerator': accelerator,
                'amount': amount,
                'pid': os.getppid()
            }
            time.sleep(2)
            print(json.dumps(result), flush=True)
    except:
        exit(1)
