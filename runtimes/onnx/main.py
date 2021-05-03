import json
import os
import pickle
import sys
import time

import onnx
import onnxruntime as rt
import traceback
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
    os.environ['CUDA_VISIBLE_DEVICES'] = accelerator
    rt.set_default_logger_severity(4)
    try:
        while True:
            # input() #
            params = '{"configuration": "/home/trever/git/hal-faas/s3cache/configs/test/tinyyolov2-7.onnx", ' \
                     '"params": {' \
                     '"payload":"/home/trever/git/hal-faas/s3cache/data/test/input_0_tiny.pb"}}'  #
            request = json.loads(params)

            # Load tensor
            tensor = onnx.TensorProto()
            with open(request['params']['payload'], 'rb') as f:
                tensor.ParseFromString(f.read())

            image_data = numpy_helper.to_array(tensor)

            # Run Inference
            start = time.time()

            # providers = [
            #     ('CUDAExecutionProvider', {
            #         'device_id': 0,
            #         'arena_extend_strategy': 'kSameAsRequested',
            #         'cuda_mem_limit': amount * 1024 * 1024,  # Amount is in MB, parameter expects bytes
            #         'cudnn_conv_algo_search': 'EXHAUSTIVE',
            #         'do_copy_in_default_stream': True,
            #     })
            # ]
            providers = ["CUDAExecutionProvider"] # Older versions of onnx apparently cannot be configured!
            # Apparently there is no good way to find out how much memory is actually used: https://pytorch.org/docs/stable/notes/faq.html
            # But lets hope this works...

            sess = rt.InferenceSession(request['configuration'], providers=providers)
            outputs = sess.get_outputs()
            output_names = list(map(lambda output: output.name, outputs))
            input_name = sess.get_inputs()[0].name

            detections = sess.run(output_names, {input_name: image_data})

            duration_ms = (time.time() - start) * 1000
            # Stop the session to hopefully release some memory...
            del sess

            # Write output to file (that will be uploaded to s3 by worker node)
            output_folder = f'./outputs/{os.getppid()}/'
            if not os.path.exists(output_folder):
                os.makedirs(output_folder)
            output_file = "output.bin"
            output_path = os.path.join(output_folder, output_file)

            with open(output_path, 'wb') as f:
                pickle.dump(detections, f)

            # Return Result to Worker Node
            metadata = {
                'inference_ms': duration_ms
            }
            result = {
                'request': request,
                'accelerator': accelerator,
                # 'amount': int(amount),
                'pid': str(os.getppid()),
                'result_type': 'reference',  # 'reference' or 'value'
                'result': [os.path.abspath(output_path)],
                'metadata': metadata
            }
            print(json.dumps(result), flush=True)
    except Exception as e:
        traceback.print_exc()
        eprint(f'Exiting pid {os.getppid()} with error {e}')
        exit(1)
