import json
import os
import pickle
import sys
import time

import onnx
import onnxruntime as rt
from onnx import numpy_helper


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, flush=True, **kwargs)


example_request = {
    'configuration': "./$PID/inputs/configuration/yolov4.onnx",
    'params': {
        'payload': "./$PID/inputs/input1.pb"
    }
}

if __name__ == '__main__':
    accelerator = sys.argv[1]
    amount = sys.argv[2]
    try:
        while True:
            params = input()
            # eprint("Got Input: ")
            # eprint(params)
            request = json.loads(params)

            # Load tensor
            tensor = onnx.TensorProto()
            with open(request['params']['payload'], 'rb') as f:
                tensor.ParseFromString(f.read())

            image_data = numpy_helper.to_array(tensor)

            # Run Inference
            start = time.time()

            sess = rt.InferenceSession(request['configuration'])
            outputs = sess.get_outputs()
            output_names = list(map(lambda output: output.name, outputs))
            input_name = sess.get_inputs()[0].name

            detections = sess.run(output_names, {input_name: image_data})

            duration_ms = (time.time() - start) * 1000

            # Write output to file (that will be uploaded to s3 by worker node)
            output_folder = f'./{os.getppid()}/outputs/'
            if not os.path.exists(output_folder):
                os.makedirs(output_folder)
            output_file = "output.bin"
            output_path = os.path.join(output_folder, output_file)

            with open(output_path, 'wb') as f:
                pickle.dump(detections, f)

            # Return Result to Worker Node
            metadata = json.dumps({
                'inference_ms': duration_ms
            })
            result = {
                'request': request,
                'accelerator': accelerator,
                'amount': amount,
                'pid': os.getppid(),
                'result_type': 'reference',  # 'reference' or 'value'
                'result': [os.path.abspath(output_path)],
                'metadata': metadata
            }
            print(json.dumps(result), flush=True)
    except:
        exit(1)
