package de.dfki.mlt.transins.example5;
/*===========================================================================
  Copyright (C) 2009-2014 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.query.IQuery;
import net.sf.okapi.common.query.QueryResult;
import net.sf.okapi.connectors.apertium.ApertiumMTConnector;
import net.sf.okapi.connectors.translatetoolkit.TranslateToolkitTMConnector;
import net.sf.okapi.lib.translation.ITMQuery;

/**
 * Example 5 from net.sf.okapi.examples
 *
 * @author Jörg Steffen, DFKI
 */
public final class Example5 {

  private Example5() {

    // private constructor to enforce noninstantiability
  }


  /**
   * @param args
   *          the arguments; not used here
   */
  public static void main(String[] args) {

    /* An implementation of the connector interface is the one for
     * the Apertium MT engine. Apertium is an open-source GMS project that offers
     * Rule-based MT capabilityies. There is also a public server that can be used.
     * This is an example on how to access such server.
     */
    QueryResult res;
    System.out.println("------------------------------------------------------------");
    System.out.println("Accessing Apertium resources");
    try (IQuery mtConnector = new ApertiumMTConnector()) {
      // official Apertium server no longer available, use own installation
      // install with:
      // apt-get install apertium-apy
      // apt-get install apertium-en-es
      // start with
      // systemctl start apertium-apy
      // check available language pairs with
      // curl http://localhost:2737/listPairs
      mtConnector.getParameters().setString("server", "http://localhost:2737/translate");
      // English to Spanish
      mtConnector.setLanguages(LocaleId.fromString("eng"), LocaleId.fromString("spa"));
      mtConnector.open();
      System.out.println("Apertium MT Service:");
      System.out.println(mtConnector.getSettingsDisplay());
      mtConnector.query("Open <b>the</b> file");
      if (mtConnector.hasNext()) {
        res = mtConnector.next();
        System.out.println("   Original: " + res.source.toText());
        System.out.println("Translation: " + res.target.toText());
      }
    }

    /* The default Translate Toolkit repository is Amagama
     * (http://translate.sourceforge.net/wiki/virtaal/amagama)
     * which includes entries from open-source software projects.
     * Okapi provide a connector to easily query the server.
     */
    System.out.println("------------------------------------------------------------");
    System.out.println("Accessing Amagama resources");
    try (ITMQuery connector = new TranslateToolkitTMConnector()) {
      connector.setLanguages(LocaleId.fromString("en"), LocaleId.fromString("fr"));
      connector.open();
      String query = "Open the file";
      connector.query(query);
      System.out.println(String.format("Amagama results for \"%s\":", query));
      while (connector.hasNext()) {
        res = connector.next();
        System.out.println("- Source: " + res.source.toText());
        System.out.println("  Target: " + res.target.toText());
      }
    }
  }
}
