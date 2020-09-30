#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Script for running REST server with endpoints for pre- and postprocessing sentences for Marian NMT
"""
import argparse
import codecs
import configparser
import logging
import re

from sacremoses import MosesPunctNormalizer, MosesTokenizer, MosesTruecaser, MosesDetruecaser, MosesDetokenizer
from subword_nmt.apply_bpe import BPE, read_vocabulary

__authors__ = ["Jörg Steffen, DFKI"]

from flask import Flask, Response, request, abort

# configure logger
logging.basicConfig(
    format="%(asctime)s: %(levelname)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    level=logging.INFO)
logger = logging.getLogger(__file__)
logger.setLevel(logging.INFO)

# languages for which a section in config exists
supported_langs = []
# misc tools for each supported language, initialized in init function
moses_punct_normalizer = None
moses_detruecaser = None
moses_tokenizer = {}
moses_detokenizer = {}
moses_truecaser = {}
bpe_encoder = {}

# Flask app
app = Flask(__name__)


@app.route('/alive')
def alive():
    """
    Simple check if server is alive
    :return: alive message
    """
    return "server is alive"


@app.route('/preprocess')
def preprocess():
    """
    Top level entry point to run preprocessing for the provided sentence.
    :return: preprocessing result
    """

    if 'lang' not in request.args:
        error_message = "missing language"
        logger.error(error_message)
        abort(400, description=error_message)
    lang = request.args.get('lang', type=str).lower()
    if lang not in supported_langs:
        error_message = f"language '{lang}' not supported"
        logger.error(error_message)
        abort(400, description=error_message)

    if 'sentence' not in request.args:
        error_message = "missing sentence"
        logger.error(error_message)
        abort(400, description=error_message)
    sentence = request.args.get('sentence', type=str)

    # normalize punctuation, this is language independent
    sentence_normalized = moses_punct_normalizer.normalize(sentence)

    # tokenize
    tokenizer = moses_tokenizer[lang]
    # a single Okapi tag is split into two tokens
    sentence_tokenized_as_tokens = tokenizer.tokenize(sentence_normalized)

    # truecasing
    truecaser = moses_truecaser[lang]
    # remove Okapi tags at beginning of sentence before truecasing and re-add them afterwards
    removed_tokens = []
    while True:
        if len(sentence_tokenized_as_tokens) > 1:
            first_token = sentence_tokenized_as_tokens[0]
            if re.search(r"\uE101", first_token) \
                    or re.search(r"\uE102", first_token) \
                    or re.search(r"\uE103", first_token):
                removed_tokens.extend(sentence_tokenized_as_tokens[0:2])
                del sentence_tokenized_as_tokens[0:2]
                continue
        break
    sentence_truecased_as_tokens = truecaser.truecase(' '.join(sentence_tokenized_as_tokens))
    sentence_truecased_as_tokens = removed_tokens + sentence_truecased_as_tokens

    # run byte pair encoding
    encoder = bpe_encoder[lang]
    sentence_bpe_as_tokens = encoder.process_line(' '.join(sentence_truecased_as_tokens)).split()

    # merge each Okapi tag in a single token
    sentence_bpe = ''
    for token in sentence_bpe_as_tokens:
        if re.search(r"\uE101", token) or re.search(r"\uE102", token) or re.search(r"\uE103", token):
            sentence_bpe += token
        else:
            sentence_bpe += token + ' '

    return Response(sentence_bpe.strip(), status=200, mimetype='text/plain')


@app.route('/postprocess')
def postprocess():
    """
    Top level entry point to run postprocessing for the provided sentence.
    :return: postprocessing result
    """

    if 'lang' not in request.args:
        error_message = "missing language"
        logger.error(error_message)
        abort(400, description=error_message)
    lang = request.args.get('lang', type=str)
    if lang not in supported_langs:
        error_message = f"language '{lang}' not supported"
        logger.error(error_message)
        abort(400, description=error_message)

    if 'sentence' not in request.args:
        error_message = "missing sentence"
        logger.error(error_message)
        abort(400, description=error_message)
    sentence = request.args.get('sentence', type=str)

    # detruecasing; this is language independent
    # remove Okapi tags at beginning of sentence before detruecasing and re-add them afterwards;
    # single Okapi tag is ONE token, in contrast to preprocessing
    sentence_tokenized_as_tokens = sentence.split()
    removed_tokens = []
    while True:
        if len(sentence_tokenized_as_tokens) > 1:
            first_token = sentence_tokenized_as_tokens[0]
            if re.search(r"\uE101", first_token) \
                    or re.search(r"\uE102", first_token) \
                    or re.search(r"\uE103", first_token):
                removed_tokens.extend(sentence_tokenized_as_tokens[0:1])
                del sentence_tokenized_as_tokens[0:1]
                continue
        break
    sentence_detruecased_as_tokens = moses_detruecaser.detruecase(' '.join(sentence_tokenized_as_tokens))
    sentence_detruecased_as_tokens = removed_tokens + sentence_detruecased_as_tokens

    # detokenize
    detokenizer = moses_detokenizer[lang]
    sentence_detokenized = detokenizer.detokenize(sentence_detruecased_as_tokens)

    return Response(sentence_detokenized, status=200, mimetype='text/plain')


def init(config):
    """
    Init global tools for each supported language
    :param config: the config with sections for each supported language
    :return:
    """

    global supported_langs

    # punctuation normalizer and detruecaser are language independent
    global moses_punct_normalizer
    global moses_detruecaser
    moses_punct_normalizer = MosesPunctNormalizer()
    moses_detruecaser = MosesDetruecaser()

    # all other tools need language specific initialization
    config_folder = config['DEFAULT']['config_folder']
    global moses_tokenizer
    global moses_detokenizer
    global moses_truecaser
    global bpe_encoder
    for lang in config.sections():
        lang = lang.lower()
        supported_langs.append(lang)
        logger.info(f"initializing for '{lang}'...")
        moses_tokenizer[lang] = MosesTokenizer(lang=lang)
        moses_detokenizer[lang] = MosesDetokenizer(lang=lang)
        moses_truecaser[lang] = MosesTruecaser(f"{config_folder}/{config[lang]['truecaser_model']}")
        bpe_encoder[lang] = BPE(
            codecs.open(f"{config_folder}/{config[lang]['bpe_codes']}", encoding='utf-8'),
            vocab=read_vocabulary(
                codecs.open(f"{config_folder}/{config[lang]['bpe_vocabulary']}", encoding='utf-8'), None))


def main(port):
    """
    The main function
    :param port: server port, None if not provided
    :return:
    """

    # load config file
    config = configparser.ConfigParser()
    config.read('src/main/resources/prepostprocessing/config.ini')

    # init globals
    init(config)

    # start REST server
    host = config['DEFAULT']['server_ip']
    if not port:
        # use port from config
        port = int(config['DEFAULT']['server_port'])
    mode = config['DEFAULT']['mode']
    if mode == 'development':
        app.run(host=host, port=port, debug=False)
    elif mode == 'production':
        from waitress import serve
        serve(app, host=host, port=port)
    else:
        logger.error(f"unsupported server mode {mode}")


def parse_arguments():
    """
    Read command line arguments, check validity and set default values
    :return: command line arguments
    """

    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--port', help="server port (optional)")

    parsed_args = parser.parse_args()

    return parsed_args


if __name__ == '__main__':
    # read command-line arguments and pass them to main function
    args = parse_arguments()
    main(args.port)
