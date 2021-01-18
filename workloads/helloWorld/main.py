import os
import signal
from http.server import BaseHTTPRequestHandler, HTTPServer
import argparse
import json
import time
import threading


# Server implementation by https://gist.github.com/bradmontgomery/2219997
class S(BaseHTTPRequestHandler):
    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()

    def do_POST(self):
        print("Got request...")
        data_string = self.rfile.read(int(self.headers['Content-Length']))
        data = json.loads(data_string)

        # Operations on data
        time.sleep(2)

        self._set_response()
        self.wfile.write(json.dumps({'request': data, 'time': time.localtime()}).encode("UTF-8"))

    def do_DELETE(self):
        threading.Thread(target=exit_func).start()
        self._set_response()


def exit_func():
    print("Exiting in 1s")
    time.sleep(1)
    print("Exiting!")
    os.kill(os.getpid(), signal.SIGINT)


def run(server_class=HTTPServer, handler_class=S, addr="localhost", port=8000):
    server_address = (addr, port)
    httpd = server_class(server_address, handler_class)

    print(f"Starting httpd server on {addr}:{port}")
    httpd.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Hello World HTTP server")
    parser.add_argument(
        "-l",
        "--listen",
        default="localhost",
        help="Specify the IP address on which the server listens",
    )
    parser.add_argument(
        "-p",
        "--port",
        type=int,
        default=8000,
        help="Specify the port on which the server listens",
    )
    args = parser.parse_args()
    run(addr=args.listen, port=args.port)
