package de.dfki.mlt.transins.example3;

/*===========================================================================
  Copyright (C) 2009 by the Okapi Framework contributors
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

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.filters.IFilterConfigurationMapper;
import net.sf.okapi.common.pipelinedriver.IPipelineDriver;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.steps.common.FilterEventsWriterStep;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;

/**
 * Example showing an Okapi pipeline running XSLT transformations on a file. The
 * transformed file is then run through the XML filter (broken down into Okapi
 * Events) and then re-written using the generic Event writer.
 */
public final class Example3 {

  private Example3() {

    // private constructor to enforce noninstantiability
  }


  /**
   * @param args
   *          the arguments; not used here
   */
  public static void main(String[] args) {

    try {
      IPipelineDriver driver = new PipelineDriver();

      IFilterConfigurationMapper fcMapper = new FilterConfigurationMapper();
      fcMapper.addConfigurations("net.sf.okapi.filters.xml.XMLFilter");
      driver.setFilterConfigurationMapper(fcMapper);

      // Input resource as URL
      URL inputXml = Example3.class.getResource("/examples/test.xml");

      // Make copy of input using identity XSLT
      InputStream in = Example3.class.getResourceAsStream("/examples/identity.xsl");
      driver.addStep(new XsltTransformStep(in));

      // Remove b tags from input using remove_b_tags XSLT
      in = Example3.class.getResourceAsStream("/examples/remove_b_tags.xsl");
      driver.addStep(new XsltTransformStep(in));

      // Filtering step - converts raw resource to events
      driver.addStep(new RawDocumentToFilterEventsStep());

      // Writer step - converts events to a raw resource
      driver.addStep(new FilterEventsWriterStep());

      // Set the info for the input and output
      RawDocument rawDoc = new RawDocument(inputXml.toURI(), "UTF-8",
          new LocaleId("en"), new LocaleId("fr"));
      rawDoc.setFilterConfigId("okf_xml");
      driver.addBatchItem(
          rawDoc, (new File("src/test/resources/examples/test.out.xml")).toURI(), "UTF-8");

      // Run the pipeline:
      // (1) XSLT identity conversion step
      // (2) XSLT replace b tag conversion step
      // (3) XML filtering step creates IResource Events
      // (4) Writer step takes Events and writes them out to outStream
      driver.processBatch();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }
}
