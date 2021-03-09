package de.dfki.mlt.transins;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;

/**
 * Demo of {@link Translator} class usage.
 *
 * @author Jörg Steffen, DFKI
 */
public final class TranslatorDemo {

  private TranslatorDemo() {

    // private constructor to enforce noninstantiability
  }


  static void testMicrosoft() {

    String sourceFileName = "src/test/resources/examples/Test.de.docx";
    String sourceLang = "de";
    String sourceEnc = "windows-1252";
    String targetFileName = "src/test/resources/examples/Test.en-from-de.out.docx";
    String targetLang = "en";
    String targetEnc = "windows-1252";
    boolean applySegmentation = true;

    Translator translator = new Translator();
    translator.translate(
        sourceFileName, sourceLang, sourceEnc,
        targetFileName, targetLang, targetEnc,
        applySegmentation, Translator.TransId.MICROSOFT);
  }


  static void testApertium() {

    String sourceFileName = "src/test/resources/examples/Test.en.docx";
    String sourceLang = "en";
    String sourceEnc = "windows-1252";
    String targetFileName = "src/test/resources/examples/Test.es-from-en.out.docx";
    String targetLang = "es";
    String targetEnc = "windows-1252";
    boolean applySegmentation = true;

    Translator translator = new Translator();
    translator.translate(
        sourceFileName, sourceLang, sourceEnc,
        targetFileName, targetLang, targetEnc,
        applySegmentation, Translator.TransId.APERTIUM);
  }


  static void testMarianFr() {

    String sourceFileName = "src/test/resources/examples/Test.fr.docx";
    String sourceLang = "fr";
    String sourceEnc = "windows-1252";
    String targetFileName = "src/test/resources/examples/Test.de-from-fr.out.docx";
    String targetLang = "de";
    String targetEnc = "windows-1252";
    boolean applySegmentation = true;
    boolean batchProcessing = false;

    Translator translator = new Translator();
    translator.translateWithMarianNmt(
        sourceFileName, sourceLang, sourceEnc,
        targetFileName, targetLang, targetEnc,
        applySegmentation, MarkupStrategy.COMPLETE_MAPPING, batchProcessing);
  }


  static void testMarianDe() {

    String sourceFileName = "src/test/resources/examples/Test.de.docx";
    String sourceLang = "de";
    String sourceEnc = "windows-1252";
    String targetFileName = "src/test/resources/examples/Test.fr-from-de.out.docx";
    String targetLang = "fr";
    String targetEnc = "windows-1252";
    boolean applySegmentation = true;
    boolean batchProcessing = true;

    Translator translator = new Translator();
    translator.translateWithMarianNmt(
        sourceFileName, sourceLang, sourceEnc,
        targetFileName, targetLang, targetEnc,
        applySegmentation, MarkupStrategy.COMPLETE_MAPPING, batchProcessing);
  }


  static void testUpperCaseDummy() {

    String sourceFileName = "src/test/resources/examples/Test.de.docx";
    String sourceLang = "de";
    String sourceEnc = "windows-1252";
    String targetFileName = "src/test/resources/examples/Test.de-uppercase.out.docx";
    String targetLang = "en";
    String targetEnc = "windows-1252";
    boolean applySegmentation = true;

    Translator translator = new Translator();
    translator.translate(
        sourceFileName, sourceLang, sourceEnc,
        targetFileName, targetLang, targetEnc,
        applySegmentation, Translator.TransId.UPPERCASE_DUMMY);
  }
}
