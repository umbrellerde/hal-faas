import paho.mqtt.client as mqtt


def main():
    c = mqtt.Client("tester")
    c.connect("172.17.0.6")
    c.publish("function-test-input", "body1", 2)
    c.publish("function-test-input", "body2", 2)


if __name__ == '__main__':
    main()
