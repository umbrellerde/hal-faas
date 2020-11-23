FROM python:3.6
RUN pip install Flask
COPY app/ app
WORKDIR app
CMD ["flask", "run", "--host=0.0.0.0"]