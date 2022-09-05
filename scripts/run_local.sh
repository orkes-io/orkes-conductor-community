#!/bin/sh
UI_PORT=1234
read -p "Enter the port for UI [1234]: " UI_PORT </dev/tty || {
  PORT=1234
}
if test -z "$UI_PORT"
then
  UI_PORT=1234
fi

SERVER_PORT=8080
read -p "Enter the port for Server [8080]: " SERVER_PORT </dev/tty || {
  SERVER_PORT=8080
}
if test -z "$SERVER_PORT"
then
  SERVER_PORT=8080
fi

docker volume create postgres
docker volume create redis

docker run --init -p $SERVER_PORT:8080 -p $UI_PORT:5000 --mount source=redis,target=/redis \
--mount source=postgres,target=/pgdata orkesio/orkes-conductor-standalone:latest



