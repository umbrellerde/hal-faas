import configparser
import os
import subprocess


def main():
    cp = configparser.RawConfigParser()
    cp.read("test.conf")
    port_start = int(cp.get("test-config", "port_start"))
    replicas = int(cp.get("test-config", "replicas"))
    invocations = int(cp.get("test-config", "invocations"))

    # Deploy functions according to config file
    # Note: the image must be built beforehand
    deploy(port_start, replicas)

    test = input("Is everything working??")

    delete_functions(replicas)

    # c = mqtt.Client("tester")
    # c.connect("172.17.0.6")
    # c.publish("function-test-input", "body1", 2)
    # c.publish("function-test-input", "body2", 2)


def deploy(port_start, replicas):
    deploy_cmd = [f'{os.getenv("HOME")}/go/bin/nuctl', "deploy", "--path", "../function/", "--platform", "local"]

    # Generate function.yaml for this run
    with open('../function/function.template.yaml', "r") as in_stream:
        template = in_stream.read()

    for i in range(replicas):
        print(f'Deploying function {i}...')
        curr = template.replace("{PORT}", str(port_start + i)).replace("{ID}", str(i))
        out = open("../function/function.yaml", "w")
        out.write(curr)
        out.close()
        subprocess.check_output(deploy_cmd)


def delete_functions(replicas):
    delete_cmd = [f'{os.getenv("HOME")}/go/bin/nuctl', "--platform", "local", "delete", "function"]
    for i in range(replicas):
        print(f'Deleting function {i}')
        delete_cmd.append(f'nuclio-function-test-{i}')
        subprocess.check_output(delete_cmd)
        delete_cmd.pop()


if __name__ == '__main__':
    main()
