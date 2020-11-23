import configparser
import subprocess
import os


def main():
    c_p = configparser.RawConfigParser()
    c_p.read("../test.conf")
    port_start = c_p.get("test-config", "port_start")
    replicas = c_p.get("test-config", "replicas")
    invocations = c_p.get("test-config", "invocations")
    nuctl = f'{os.getenv("HOME")}/go/bin/nuctl'
    print(subprocess.check_output([nuctl, "get", "functions", "--platform", "local"]))


if __name__ == '__main__':
    main()
