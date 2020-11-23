from flask import Flask, request

app = Flask(__name__)


# http://172.17.0.1:5000
@app.route('/', methods=['GET', 'POST'])
def collect_result():
    if request.method == "POST":
        print("Got POST!")
        print(str(request.get_data()))
        return ""
    else:
        return "Hello from Flask"
