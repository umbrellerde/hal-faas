import json
import os
import pickle
import sys
import time

import onnx
import onnxruntime as rt
rt.set_default_logger_severity(4)
#rt.capi._pybind_state.set_openvino_device("MYRIAD_FP16")
import traceback
from onnx import numpy_helper
import socket


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, flush=True, **kwargs)


example_request = {
    'configuration': "/home/user/hal-faas/s3cache/configs/test/tinyyolov2-7.onnx",
    'params': {
        'payload': "/home/user/hal-faas/s3cache/data/test/input_0_tiny.pb"
    }
}

#{"configuration": "/home/user/hal-faas/s3cache/configs/test/tinyyolov2-7.onnx","params": {"payload": "/home/user/hal-faas/s3cache/data/test/input_0_tiny.pb"}}

host_dir = "/code/host"

if __name__ == '__main__':
    accelerator = sys.argv[0]
    amount = sys.argv[1]
    try:
        while True:
            # input() #
            params = input() #'{"configuration": "model.onnx", "params": {"payload":"test_data_set_0/input_0.pb"}}'  # TODO read from hal-faas
            request = json.loads(params)
            request['configuration'] = host_dir + request['configuration']
            request['params']['payload'] = host_dir + request['params']['payload']


            # Load tensor
            tensor = onnx.TensorProto()
            with open(request['params']['payload'], 'rb') as f:
                tensor.ParseFromString(f.read())

            image_data = numpy_helper.to_array(tensor)

            # Run Inference
            start = time.time()

            providers = ['OpenVINOExecutionProvider']
            # Apparently there is no good way to find out how much memory is actually used: https://pytorch.org/docs/stable/notes/faq.html
            # But lets hope this works...

            sess = rt.InferenceSession(request['configuration'], providers=providers)
            outputs = sess.get_outputs()
            output_names = list(map(lambda output: output.name, outputs))
            input_name = sess.get_inputs()[0].name

            detections = sess.run(output_names, {input_name: image_data})

            end = time.time()
            duration_ms = (end - start) * 1000
            # Stop the session to hopefully release some memory...
            del sess

            # Write output to file (that will be uploaded to s3 by worker node)
            output_folder = f'{host_dir}/home/user/hal-faas/runtimes/onnx-stick/outputs/{os.getppid()}/'
            if not os.path.exists(output_folder):
                os.makedirs(output_folder)
                os.chown(output_folder, 1000, 1000)
            output_file = "output.bin"
            output_path = os.path.join(output_folder, output_file)

            with open(output_path, 'wb') as f:
                pickle.dump(detections, f)
            
            os.chown(output_path, 1000, 1000)

            # Return Result to Worker Node
            metadata = {
                'inference_ms': duration_ms,
                'hostname': socket.gethostname(),
                'start': start * 1000,
                'end': end * 1000
            }
            result = {
                'request': request,
                'accelerator': accelerator,
                # 'amount': int(amount),
                'pid': str(os.getppid()),
                'result_type': 'reference',  # 'reference' or 'value'
                'result': [output_path.replace(host_dir, "")],
                'metadata': metadata
            }
            print(json.dumps(result), flush=True)
    except Exception as e:
        traceback.print_exc()
        eprint(f'Exiting pid {os.getppid()} with error {e}')
        exit(1)
