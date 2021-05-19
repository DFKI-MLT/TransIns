#!/bin/bash
sudo -- sh -c 'export DOCKER_BUILDKIT=1; \
  docker build -t dfki/transins-prepost:0.0.1 -f Dockerfile-PrePostprocessing . ; \
  docker build -t dfki/transins-marian:0.0.1 -f Dockerfile-MarianNMT . ; \
  docker build -t dfki/transins-server:0.0.1 -f Dockerfile-Server .'
