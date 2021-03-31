import re

from six import text_type

from sacremoses import MosesTokenizer, MosesDetokenizer


class MosesTokenizerExtended(MosesTokenizer):
    """
    Extended Moses tokenizer with Catalan support
    """

    CA_MIDDLE_DOT = r"([^{}\s\.'\`\,\-·])".format(MosesTokenizer.IsAlnum), r" \1 "
    CA_MIDDLE_DOT_NO_LOWER_FOLLOW = r"(·)([^a-z])", r"\1 · \2"

    def tokenize(
            self,
            text,
            aggressive_dash_splits=False,
            return_str=False,
            escape=True,
            protected_patterns=None,
    ):
        """
        Python port of the Moses tokenizer.

            :param tokens: A single string, i.e. sentence text.
            :type tokens: str
            :param aggressive_dash_splits: Option to trigger dash split rules .
            :type aggressive_dash_splits: bool
        """

        # Converts input string into unicode.
        text = text_type(text)
        # De-duplicate spaces and clean ASCII junk
        for regexp, substitution in [self.DEDUPLICATE_SPACE, self.ASCII_JUNK]:
            text = re.sub(regexp, substitution, text)

        if protected_patterns:
            # Find the tokens that needs to be protected.
            protected_tokens = [
                match.group()
                for protected_pattern in protected_patterns
                for match in re.finditer(protected_pattern, text, re.IGNORECASE)
            ]
            # Apply the protected_patterns.
            for i, token in enumerate(protected_tokens):
                substituition = "THISISPROTECTED" + str(i).zfill(3)
                text = text.replace(token, substituition)

        # Strips heading and trailing spaces.
        text = text.strip()

        # FIXME!!!
        '''
        # For Finnish and Swedish, seperate out all "other" special characters.
        if self.lang in ["fi", "sv"]:
            # In Finnish and Swedish, the colon can be used inside words
            # as an apostrophe-like character:
            # USA:n, 20:een, EU:ssa, USA:s, S:t
            regexp, substitution = self.FI_SV_COLON_APOSTROPHE
            text = re.sub(regexp, substitution, text)
            # If a colon is not immediately followed by lower-case characters,
            # separate it out anyway.
            regexp, substitution = self.FI_SV_COLON_NO_LOWER_FOLLOW
            text = re.sub(regexp, substitution, text)
        else:
        '''
        if self.lang in ["ca"]:
            # In Catalan, the middle dot can be used inside words:
            # il·lusio
            regexp, substitution = self.CA_MIDDLE_DOT
            text = re.sub(regexp, substitution, text)
            # If a middle dot is not immediately followed by lower-case characters,
            # separate it out anyway.
            regexp, substitution = self.CA_MIDDLE_DOT_NO_LOWER_FOLLOW
            text = re.sub(regexp, substitution, text)
        else:
            # Separate special characters outside of IsAlnum character set.
            regexp, substitution = self.PAD_NOT_ISALNUM
            text = re.sub(regexp, substitution, text)

        # Aggressively splits dashes
        if aggressive_dash_splits:
            regexp, substitution = self.AGGRESSIVE_HYPHEN_SPLIT
            text = re.sub(regexp, substitution, text)

        # Replaces multidots with "DOTDOTMULTI" literal strings.
        text = self.replace_multidots(text)

        # Separate out "," except if within numbers e.g. 5,300
        for regexp, substitution in [
            self.COMMA_SEPARATE_1,
            self.COMMA_SEPARATE_2,
            self.COMMA_SEPARATE_3,
        ]:
            text = re.sub(regexp, substitution, text)

        # (Language-specific) apostrophe tokenization.
        if self.lang == "en":
            for regexp, substitution in self.ENGLISH_SPECIFIC_APOSTROPHE:
                text = re.sub(regexp, substitution, text)
        elif self.lang in ["fr", "it", "ca"]:
            for regexp, substitution in self.FR_IT_SPECIFIC_APOSTROPHE:
                text = re.sub(regexp, substitution, text)
        # FIXME!!!
        ##elif self.lang == "so":
        ##    for regexp, substitution in self.SO_SPECIFIC_APOSTROPHE:
        ##        text = re.sub(regexp, substitution, text)
        else:
            regexp, substitution = self.NON_SPECIFIC_APOSTROPHE
            text = re.sub(regexp, substitution, text)

        # Handles nonbreaking prefixes.
        text = self.handles_nonbreaking_prefixes(text)
        # Cleans up extraneous spaces.
        regexp, substitution = self.DEDUPLICATE_SPACE
        text = re.sub(regexp, substitution, text).strip()
        # Split trailing ".'".
        regexp, substituition = self.TRAILING_DOT_APOSTROPHE
        text = re.sub(regexp, substituition, text)

        # Restore the protected tokens.
        if protected_patterns:
            for i, token in enumerate(protected_tokens):
                substituition = "THISISPROTECTED" + str(i).zfill(3)
                text = text.replace(substituition, token)

        # Restore multidots.
        text = self.restore_multidots(text)
        if escape:
            # Escape XML symbols.
            text = self.escape_xml(text)

        return text if return_str else text.split()


class MosesDetokenizerExtended(MosesDetokenizer):
    """
    Extended Moses detokenizer with Catalan support
    """

    def detokenize(self, tokens, return_str=True, unescape=True):

        if self.lang == 'ca':
            # simulate Catalan detokenization by first applying the French detokenizer
            # and then the Spanish detokenizer
            self.lang = 'fr'
            detokenized_text = self.tokenize(tokens, return_str, unescape)
            tokens = detokenized_text.split(" ")
            self.lang = 'es'
            detokenized_text = self.tokenize(tokens, return_str, unescape)
            self.lang = 'ca'
            return detokenized_text
        else:
            return self.tokenize(tokens, return_str, unescape)
