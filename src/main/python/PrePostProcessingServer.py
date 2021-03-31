#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Script for running REST server with endpoints for pre- and postprocessing sentences for Marian NMT
"""
import argparse
import codecs
import configparser
import json
import logging
import re

from sacremoses import MosesPunctNormalizer, MosesTruecaser, MosesDetruecaser
from subword_nmt.apply_bpe import BPE, read_vocabulary

from tokenizeExtended import MosesTokenizerExtended, MosesDetokenizerExtended

__authors__ = ["Jörg Steffen, DFKI"]

from flask import Flask, Response, request, abort

# configure logger
logging.basicConfig(
    format="%(asctime)s: %(levelname)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    level=logging.INFO)
logger = logging.getLogger(__file__)
logger.setLevel(logging.INFO)

# the configuration, read from config.ini
configuration = None

# translation directions for which a section in config exists
supported_trans_dirs = []

# misc tools, initialized in init function;
# language independent:
moses_detruecaser = None
# language dependent:
moses_punct_normalizer = {}
moses_tokenizer = {}
moses_detokenizer = {}
# translation direction dependent
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


@app.route('/preprocess', methods=['GET', 'POST'])
def preprocess():
    """
    Top level entry point to run sentence preprocessing.
    :return: preprocessing result
    """

    if 'trans_dir' not in request.args:
        error_message = "missing translation direction"
        logger.error(error_message)
        abort(400, description=error_message)
    trans_dir = request.args.get('trans_dir', type=str).lower()
    if trans_dir not in supported_trans_dirs:
        error_message = f"translation direction '{trans_dir}' not supported"
        logger.error(error_message)
        abort(400, description=error_message)

    if request.method == 'GET':
        if 'sentence' not in request.args:
            error_message = "missing sentence"
            logger.error(error_message)
            abort(400, description=error_message)
        sentence = request.args.get('sentence', type=str)
        preprocessed_sentence = preprocess_sentence(sentence, trans_dir)
        return Response(preprocessed_sentence, status=200, mimetype='text/plain')

    elif request.method == 'POST':
        if request.mimetype != 'application/json':
            # use mimetype since we don't need the other information provided by content_type
            error_message = f"Invalid content-type '{request.mimetype}'. Must be application/json."
            logger.error(error_message)
            abort(400, description=error_message)
        sentences = request.get_json()
        preprocessed_sentences = []
        for sentence in sentences:
            preprocessed_sentences.append(preprocess_sentence(sentence, trans_dir))
        preprocessed_sentences_as_json = json.dumps(preprocessed_sentences)
        return Response(preprocessed_sentences_as_json, status=200, mimetype='application/json')


def preprocess_sentence(sentence, trans_dir):
    """
    Preprocess the given sentence for the given translation direction.
    :param sentence: the sentence to preprocess
    :param trans_dir: the translation direction
    :return: preprocessing result
    """

    # extract source language from translation direction
    source_lang = trans_dir.split('-')[0]

    # normalize punctuation; this is translation direction dependent
    normalizer = moses_punct_normalizer[source_lang]
    if configuration[trans_dir]['replace_unicode_punctuation']:
        sentence = normalizer.replace_unicode_punct(sentence)
    sentence_normalized = normalizer.normalize(sentence)

    # tokenize; this is language dependent
    tokenizer = moses_tokenizer[source_lang]
    # a single Okapi tag is split into two tokens
    sentence_tokenized_as_tokens = tokenizer.tokenize(sentence_normalized)

    # truecasing; this is translation direction dependent
    truecaser = moses_truecaser[trans_dir]
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

    # run byte pair encoding; this is translation direction dependent
    encoder = bpe_encoder[trans_dir]
    sentence_bpe_as_tokens = encoder.process_line(' '.join(sentence_truecased_as_tokens)).split()

    # merge each Okapi tag in a single token
    sentence_bpe = ''
    for token in sentence_bpe_as_tokens:
        if re.search(r"\uE101", token) or re.search(r"\uE102", token) or re.search(r"\uE103", token):
            sentence_bpe += token
        else:
            sentence_bpe += token + ' '
    return sentence_bpe.strip()


@app.route('/postprocess', methods=['GET', 'POST'])
def postprocess():
    """
    Top level entry point to run sentence postprocessing.
    :return: postprocessing result
    """

    if 'trans_dir' not in request.args:
        error_message = "missing translation direction"
        logger.error(error_message)
        abort(400, description=error_message)
    trans_dir = request.args.get('trans_dir', type=str)
    if trans_dir not in supported_trans_dirs:
        error_message = f"translation direction '{trans_dir}' not supported"
        logger.error(error_message)
        abort(400, description=error_message)

    if request.method == 'GET':
        if 'sentence' not in request.args:
            error_message = "missing sentence"
            logger.error(error_message)
            abort(400, description=error_message)
        sentence = request.args.get('sentence', type=str)
        postprocessed_sentence = postprocess_sentence(sentence, trans_dir)
        return Response(postprocessed_sentence, status=200, mimetype='text/plain')

    elif request.method == 'POST':
        if request.mimetype != 'application/json':
            # use mimetype since we don't need the other information provided by content_type
            error_message = f"Invalid content-type '{request.mimetype}'. Must be application/json."
            logger.error(error_message)
            abort(400, description=error_message)
        sentences = request.get_json()
        postprocessed_sentences = []
        for sentence in sentences:
            postprocessed_sentences.append(postprocess_sentence(sentence, trans_dir))
        postprocessed_sentences_as_json = json.dumps(postprocessed_sentences)
        return Response(postprocessed_sentences_as_json, status=200, mimetype='application/json')


def postprocess_sentence(sentence, trans_dir):
    """
    Postprocess the given sentence for the given translation direction.
    :param sentence: the sentence to postprocess
    :param trans_dir: the translation direction
    :return: postprocessing result
    """

    # extract target language from translation direction
    target_lang = trans_dir.split('-')[1]

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

    # detokenize; this is language dependent
    detokenizer = moses_detokenizer[target_lang]
    return detokenizer.detokenize(sentence_detruecased_as_tokens)


def init(config, config_folder):
    """
    Init global tools for each supported translation direction
    :param config: the config with sections for each supported translation direction
    :param config_folder: the config folder
    :return:
    """

    global configuration
    configuration = config

    global supported_trans_dirs

    # detruecaser is language independent
    global moses_detruecaser
    moses_detruecaser = MosesDetruecaser()

    # punctuation normalizer, tokenizer and detokenizer are language dependent
    global moses_punct_normalizer
    global moses_tokenizer
    global moses_detokenizer
    # truecaser and byte pair encoder are translation direction dependent;
    # truecaser depends on the corpus on which the translation model was trained
    # and is therefore considered translation direction dependent
    global moses_truecaser
    global bpe_encoder

    for trans_dir in config.sections():
        trans_dir = trans_dir.lower()
        supported_trans_dirs.append(trans_dir)
        logger.info(f"initializing for '{trans_dir}'...")
        for lang in trans_dir.split('-'):
            # initialize punctuation normalizer, tokenizer and detokenizer ONCE for each language
            if lang not in moses_punct_normalizer:
                moses_punct_normalizer[lang] = MosesPunctNormalizer(lang=lang)
                moses_tokenizer[lang] = MosesTokenizerExtended(lang=lang)
                moses_detokenizer[lang] = MosesDetokenizerExtended(lang=lang)
        moses_truecaser[trans_dir] = MosesTruecaser(f"{config_folder}/{config[trans_dir]['truecaser_model']}")
        bpe_encoder[trans_dir] = BPE(
            codecs.open(f"{config_folder}/{config[trans_dir]['bpe_codes']}", encoding='utf-8'),
            vocab=read_vocabulary(
                codecs.open(f"{config_folder}/{config[trans_dir]['bpe_vocabulary']}", encoding='utf-8'), None))
    logger.info("initialization done")


def main(config_folder, port):
    """
    The main function
    :param config_folder: config folder
    :param port: server port, None if not provided
    :return:
    """

    # load config file
    config = configparser.ConfigParser()
    if not config.read(f"{config_folder}/config.ini"):
        logger.error("provided config folder not found or no config.ini found in config folder")
        exit(1)

    # init globals
    init(config, config_folder)

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
    parser.add_argument('-cf', '--config_folder', help="config folder")
    parser.add_argument('-p', '--port', help="server port (optional)")

    parsed_args = parser.parse_args()

    # check required --config-folder argument
    if not parsed_args.config_folder:
        parser.print_help()
        exit(1)

    return parsed_args


if __name__ == '__main__':
    # read command-line arguments and pass them to main function
    args = parse_arguments()
    main(args.config_folder, args.port)
