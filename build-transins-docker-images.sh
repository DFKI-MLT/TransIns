#!/bin/bash
sudo -- sh -c 'export DOCKER_BUILDKIT=1; \
  docker build -t dfki/transins-prepost:1.0.0 -f Dockerfile-PrePostprocessing . ; \
  docker build -t dfki/transins-marian:1.0.0 -f Dockerfile-MarianNMT . ; \
  docker build -t dfki/transins-server:1.0.0 -f Dockerfile-Server .'
