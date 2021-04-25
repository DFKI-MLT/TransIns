#!/bin/bash

function install_model {

  model=$1
  trans_dir=$2

  mkdir -p marian-nmt/$trans_dir
  mkdir -p prepostprocessing/$trans_dir
  unzip -o $model -d marian-nmt/$trans_dir
  chmod 644 marian-nmt/$trans_dir/*
  mv -vf \
    marian-nmt/$trans_dir/*.sh \
    marian-nmt/$trans_dir/*.spm \
    marian-nmt/$trans_dir/*.tcmodel \
    prepostprocessing/$trans_dir
  rm -fv $model
}


cd src/main/resources

# de-fr
wget https://object.pouta.csc.fi/OPUS-MT-models/de-fr/opus-2020-01-08.zip -O model.zip
install_model model.zip de-fr-mono-opus

# fr-de
wget https://object.pouta.csc.fi/OPUS-MT-models/fr-de/opus-2020-01-09.zip -O model.zip
install_model model.zip fr-de-mono-opus

# de-en
wget https://object.pouta.csc.fi/OPUS-MT-models/de-en/opus-2020-02-26.zip -O model.zip
install_model model.zip de-en-mono-opus

# en-de
wget https://object.pouta.csc.fi/OPUS-MT-models/en-de/opus-2020-02-26.zip -O model.zip
install_model model.zip en-de-mono-opus
