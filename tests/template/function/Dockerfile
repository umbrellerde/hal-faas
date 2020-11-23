ARG NUCLIO_LABEL=1.1.18
ARG NUCLIO_ARCH=amd64
ARG NUCLIO_BASE_IMAGE=nvidia/cuda:11.0-base-ubuntu20.04
ARG NUCLIO_ONBUILD_IMAGE=quay.io/nuclio/handler-builder-python-onbuild:${NUCLIO_LABEL}-${NUCLIO_ARCH}

# Supplies processor uhttpc, used for healthcheck
FROM nuclio/uhttpc:0.0.1-amd64 as uhttpc

# Supplies processor binary, wrapper
FROM ${NUCLIO_ONBUILD_IMAGE} as processor

# From the base image
FROM nvidia/cuda:11.0-base-ubuntu20.04

# Copy required objects from the suppliers
COPY --from=processor /home/nuclio/bin/processor /usr/local/bin/processor
COPY --from=processor /home/nuclio/bin/py /opt/nuclio/
COPY --from=uhttpc /home/nuclio/bin/uhttpc /usr/local/bin/uhttpc

# apt upgrade -y &&
RUN apt update && apt install -y python3 python3-pip
RUN pip3 install nuclio-sdk msgpack --no-index --find-links /opt/nuclio/whl
#RUN pip3 install requests tensorflow

# Readiness probe
HEALTHCHECK --interval=1s --timeout=3s CMD /usr/local/bin/uhttpc --url http://127.0.0.1:8082/ready || exit 1

# USER CONTENT
COPY requirements.txt .
RUN pip3 install -r requirements.txt
ADD ./main.py /opt/nuclio
COPY . /opt/nuclio
ADD function.yaml /etc/nuclio/config/processor/processor.yaml
# END OF USER CONTENT

# Run processor with configuration and platform configuration
CMD [ "processor" ]